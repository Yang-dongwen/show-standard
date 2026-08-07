package com.ddmo.app.service;

import com.ddmo.app.config.AppBackupProperties;
import com.ddmo.app.config.DbDialect;
import com.ddmo.app.security.TenantContext;
import com.ddmo.app.util.SnowflakeIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Service
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final JdbcTemplate jdbcTemplate;
    private final AppBackupProperties backupProperties;
    private final SnowflakeIdGenerator idGenerator;
    private final DbDialect dbDialect;

    public BackupService(
        JdbcTemplate jdbcTemplate,
        AppBackupProperties backupProperties,
        SnowflakeIdGenerator idGenerator,
        DbDialect dbDialect
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.backupProperties = backupProperties;
        this.idGenerator = idGenerator;
        this.dbDialect = dbDialect;
    }

    public Map<String, Object> createBackup() {
        assertSqliteBackupSupported();
        Path db = dbPath();
        if (!Files.exists(db)) {
            throw new IllegalStateException("数据库文件不存在: " + db);
        }
        try {
            // 尽量刷盘 WAL
            try {
                jdbcTemplate.execute("PRAGMA wal_checkpoint(FULL)");
            } catch (Exception e) {
                log.warn("wal_checkpoint 失败，继续备份: {}", e.getMessage());
            }

            Path dir = Path.of(backupProperties.getDir());
            Files.createDirectories(dir);
            String name = "show-" + LocalDateTime.now().format(STAMP) + ".db";
            Path target = dir.resolve(name);
            Files.copy(db, target, StandardCopyOption.REPLACE_EXISTING);

            // 可选复制 wal/shm 一般 checkpoint 后可不需要
            recordSystemLog("BACKUP", "创建备份: " + name);

            Map<String, Object> result = new HashMap<>();
            result.put("fileName", name);
            result.put("path", target.toAbsolutePath().toString());
            result.put("size", Files.size(target));
            result.put("createdAt", LocalDateTime.now().toString());
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("备份失败: " + e.getMessage(), e);
        }
    }

    public Path resolveBackupFile(String fileName) {
        if (fileName == null || fileName.isBlank() || fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            throw new IllegalArgumentException("非法备份文件名");
        }
        if (!fileName.endsWith(".db")) {
            throw new IllegalArgumentException("仅支持 .db 备份文件");
        }
        Path file = Path.of(backupProperties.getDir()).resolve(fileName).normalize();
        Path dir = Path.of(backupProperties.getDir()).toAbsolutePath().normalize();
        if (!file.startsWith(dir)) {
            throw new IllegalArgumentException("非法备份路径");
        }
        if (!Files.exists(file)) {
            throw new IllegalArgumentException("备份文件不存在");
        }
        return file;
    }

    public List<Map<String, Object>> listBackups() {
        if (!dbDialect.isFileDatabase()) {
            return List.of();
        }
        Path dir = Path.of(backupProperties.getDir());
        List<Map<String, Object>> list = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return list;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".db"))
                .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
                .forEach(p -> {
                    try {
                        Map<String, Object> row = new HashMap<>();
                        row.put("fileName", p.getFileName().toString());
                        row.put("size", Files.size(p));
                        row.put("lastModified", Files.getLastModifiedTime(p).toString());
                        list.add(row);
                    } catch (IOException ignored) {
                        // skip unreadable
                    }
                });
        } catch (IOException e) {
            throw new IllegalStateException("列举备份失败: " + e.getMessage(), e);
        }
        return list;
    }

    /**
     * 将上传/指定备份写入 restore-pending.db，需重启应用后生效。
     */
    public Map<String, Object> scheduleRestoreFromBackup(String fileName) {
        assertSqliteBackupSupported();
        Path source = resolveBackupFile(fileName);
        return scheduleRestoreFromPath(source, fileName);
    }

    public Map<String, Object> scheduleRestoreFromUpload(InputStream in, String originalName) {
        assertSqliteBackupSupported();
        try {
            Path dir = Path.of(System.getProperty("user.home"), ".show");
            Files.createDirectories(dir);
            Path temp = dir.resolve("upload-restore-" + System.currentTimeMillis() + ".db");
            Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
            validateSqliteHeader(temp);
            Map<String, Object> result = scheduleRestoreFromPath(temp, originalName == null ? temp.getFileName().toString() : originalName);
            Files.deleteIfExists(temp);
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("上传恢复文件失败: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> scheduleRestoreFromPath(Path source, String label) {
        try {
            validateSqliteHeader(source);
            Path dir = Path.of(System.getProperty("user.home"), ".show");
            Files.createDirectories(dir);
            Path pending = dir.resolve("restore-pending.db");
            Files.copy(source, pending, StandardCopyOption.REPLACE_EXISTING);
            recordSystemLog("RESTORE", "已排队恢复: " + label + "，请重启应用");

            Map<String, Object> result = new HashMap<>();
            result.put("pending", true);
            result.put("message", "恢复文件已就绪，请重启应用以完成恢复");
            result.put("source", label);
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("准备恢复失败: " + e.getMessage(), e);
        }
    }

    private void validateSqliteHeader(Path file) throws IOException {
        byte[] header = new byte[16];
        try (InputStream in = Files.newInputStream(file)) {
            int n = in.read(header);
            if (n < 16) {
                throw new IllegalArgumentException("不是有效的 SQLite 数据库文件");
            }
        }
        String magic = new String(header, 0, 15);
        if (!"SQLite format 3".equals(magic)) {
            throw new IllegalArgumentException("不是有效的 SQLite 数据库文件");
        }
    }

    /**
     * 从当前 JDBC URL 解析 SQLite 文件路径；无法解析时回退 ~/.show/show.db。
     */
    private Path dbPath() {
        String url = dbDialect.jdbcUrl();
        if (url != null) {
            String u = url.trim();
            String prefix = "jdbc:sqlite:";
            if (u.regionMatches(true, 0, prefix, 0, prefix.length())) {
                String filePart = u.substring(prefix.length()).trim();
                // jdbc:sqlite::memory: 等非文件
                if (!filePart.isEmpty() && !filePart.startsWith(":") && !filePart.equalsIgnoreCase("memory")) {
                    return Path.of(filePart);
                }
            }
        }
        return Path.of(System.getProperty("user.home"), ".show", "show.db");
    }

    private void assertSqliteBackupSupported() {
        if (!dbDialect.isFileDatabase()) {
            throw new IllegalArgumentException(
                "当前为云版 " + dbDialect.label() + " 数据源，不支持 SQLite 文件备份/恢复；请使用 mysqldump 等工具备份"
            );
        }
    }

    private void recordSystemLog(String action, String detail) {
        try {
            long tenantId = TenantContext.getTenantId();
            jdbcTemplate.update("""
                    INSERT INTO t_audit_log(id, tenant_id, action, entity_type, entity_id, detail, created_at)
                    VALUES (?, ?, ?, '系统', '0', ?, CURRENT_TIMESTAMP)
                    """,
                idGenerator.nextId(),
                tenantId,
                "BACKUP".equals(action) ? "数据备份" : "数据恢复",
                detail
            );
        } catch (Exception e) {
            log.debug("记录备份审计失败: {}", e.getMessage());
        }
    }
}
