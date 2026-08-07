package com.ddmo.app.install;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 安装向导：首次选型（本地买断 / SaaS 云）并落盘配置。
 */
@Service
public class InstallService {

    private static final Logger log = LoggerFactory.getLogger(InstallService.class);

    private final Environment environment;
    private final JdbcTemplate jdbcTemplate;

    public InstallService(Environment environment, JdbcTemplate jdbcTemplate) {
        this.environment = environment;
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> status() {
        ensureShowDir();
        maybeAutoCompleteLegacyLocal();

        boolean completed = isCompleted();
        String edition = readProp("install.edition", completed ? inferRunningEdition() : "");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("needsSetup", !completed);
        m.put("completed", completed);
        m.put("edition", edition);
        m.put("editionLabel", "saas".equals(edition) ? "SaaS 云版" : ("local".equals(edition) ? "本地买断版" : ""));
        m.put("installFile", InstallPaths.installFile().toAbsolutePath().toString());
        m.put("runningDeployment", environment.getProperty("app.deployment", "desktop"));
        m.put("runningDb", detectDbLabel(environment.getProperty("spring.datasource.url", "")));
        m.put("skus", skus());
        m.put("restartRequired", false);
        return m;
    }

    public Map<String, Object> testMysql(InstallCompleteRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        String url = buildMysqlUrl(req);
        String user = blankTo(req.getMysqlUsername(), "show");
        String pass = req.getMysqlPassword() == null ? "" : req.getMysqlPassword();
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            try (Connection c = DriverManager.getConnection(url, user, pass)) {
                boolean ok = c.isValid(5);
                if (!ok) {
                    throw new IllegalArgumentException("MySQL 连接无效");
                }
            }
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("缺少 MySQL 驱动");
        } catch (Exception e) {
            throw new IllegalArgumentException("无法连接 MySQL: " + rootMessage(e));
        }
        Map<String, Object> m = new HashMap<>();
        m.put("ok", true);
        m.put("url", url);
        m.put("message", "连接成功");
        return m;
    }

    public Map<String, Object> complete(InstallCompleteRequest req) {
        if (isCompleted()) {
            throw new IllegalArgumentException("安装已完成，如需重装请删除 " + InstallPaths.installFile());
        }
        if (req == null || req.getEdition() == null || req.getEdition().isBlank()) {
            throw new IllegalArgumentException("请选择安装类型");
        }
        String edition = req.getEdition().trim().toLowerCase();
        ensureShowDir();

        Properties p = new Properties();
        p.setProperty("install.completed", "true");
        p.setProperty("install.completedAt", Instant.now().toString());

        boolean restartRequired;
        if ("local".equals(edition) || "desktop".equals(edition) || "buyout".equals(edition)) {
            p.setProperty("install.edition", "local");
            p.setProperty("app.deployment", "desktop");
            p.setProperty("spring.datasource.url", "jdbc:sqlite:" + InstallPaths.sqliteDb().toAbsolutePath());
            p.setProperty("spring.datasource.driver-class-name", "org.sqlite.JDBC");
            p.setProperty("spring.datasource.username", "show");
            p.setProperty("spring.datasource.password", "show");
            p.setProperty("app.wx.miniapp.enabled", "false");
            // 当前进程已是本地 SQLite 时无需重启
            String cur = environment.getProperty("spring.datasource.url", "");
            restartRequired = cur.toLowerCase().contains("mysql");
        } else if ("saas".equals(edition) || "cloud".equals(edition)) {
            testMysql(req);
            String url = buildMysqlUrl(req);
            p.setProperty("install.edition", "saas");
            p.setProperty("app.deployment", "cloud");
            p.setProperty("spring.profiles.active", "cloud");
            p.setProperty("spring.datasource.url", url);
            p.setProperty("spring.datasource.username", blankTo(req.getMysqlUsername(), "show"));
            p.setProperty("spring.datasource.password", req.getMysqlPassword() == null ? "" : req.getMysqlPassword());
            p.setProperty("spring.datasource.driver-class-name", "com.mysql.cj.jdbc.Driver");
            p.setProperty("app.wx.miniapp.enabled", req.isEnableMiniProgram() ? "true" : "false");
            restartRequired = true;
        } else {
            throw new IllegalArgumentException("未知安装类型，请选择 local 或 saas");
        }

        Path file = InstallPaths.installFile();
        try (OutputStream out = Files.newOutputStream(file)) {
            p.store(out, "Show product install choice - do not edit while app running");
        } catch (IOException e) {
            throw new IllegalStateException("写入安装配置失败: " + e.getMessage(), e);
        }
        log.info("安装向导完成 edition={} file={}", p.getProperty("install.edition"), file);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("completed", true);
        m.put("edition", p.getProperty("install.edition"));
        m.put("editionLabel", "saas".equals(p.getProperty("install.edition")) ? "SaaS 云版" : "本地买断版");
        m.put("restartRequired", restartRequired);
        m.put("message", restartRequired
            ? "已保存 SaaS 配置，请重启应用后生效（将连接 MySQL 并启用运营台/小程序能力）"
            : "已选择本地买断版，可直接登录使用");
        m.put("installFile", file.toAbsolutePath().toString());
        return m;
    }

    public boolean isCompleted() {
        Properties p = loadProps();
        return p != null && "true".equalsIgnoreCase(p.getProperty("install.completed", ""));
    }

    public void assertSetupDone() {
        maybeAutoCompleteLegacyLocal();
        if (!isCompleted()) {
            throw new IllegalArgumentException("请先完成安装向导，选择「本地买断」或「SaaS 云版」");
        }
    }

    private void maybeAutoCompleteLegacyLocal() {
        if (isCompleted()) {
            return;
        }
        // 开发调试：-Dinstall.forceWizard=true 强制进入向导
        if ("true".equalsIgnoreCase(System.getProperty("install.forceWizard", ""))) {
            return;
        }
        // 已有业务库 → 视为历史本地安装，静默完成，避免老用户被向导拦住
        if (!Files.isRegularFile(InstallPaths.sqliteDb())) {
            return;
        }
        try {
            Long n = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM t_manager", Long.class);
            if (n != null && n > 0) {
                Properties p = new Properties();
                p.setProperty("install.completed", "true");
                p.setProperty("install.edition", "local");
                p.setProperty("install.completedAt", Instant.now().toString());
                p.setProperty("install.legacyAuto", "true");
                p.setProperty("app.deployment", "desktop");
                try (OutputStream out = Files.newOutputStream(InstallPaths.installFile())) {
                    p.store(out, "Auto-completed legacy local install");
                }
                log.info("检测到已有本地数据，已自动标记为本地买断版");
            }
        } catch (Exception e) {
            log.debug("legacy auto-complete skip: {}", e.getMessage());
        }
    }

    private Properties loadProps() {
        Path file = InstallPaths.installFile();
        if (!Files.isRegularFile(file)) {
            return null;
        }
        Properties p = new Properties();
        try (var in = Files.newInputStream(file)) {
            p.load(in);
            return p;
        } catch (IOException e) {
            return null;
        }
    }

    private String readProp(String key, String def) {
        Properties p = loadProps();
        if (p == null) {
            return def;
        }
        return p.getProperty(key, def);
    }

    private String inferRunningEdition() {
        String d = environment.getProperty("app.deployment", "desktop");
        return "cloud".equalsIgnoreCase(d) ? "saas" : "local";
    }

    private static String detectDbLabel(String url) {
        if (url == null) {
            return "unknown";
        }
        String u = url.toLowerCase();
        if (u.contains("sqlite")) {
            return "sqlite";
        }
        if (u.contains("mysql")) {
            return "mysql";
        }
        return "other";
    }

    private static String buildMysqlUrl(InstallCompleteRequest req) {
        if (req.getMysqlUrl() != null && !req.getMysqlUrl().isBlank()) {
            return req.getMysqlUrl().trim();
        }
        String host = blankTo(req.getMysqlHost(), "127.0.0.1");
        int port = req.getMysqlPort() > 0 ? req.getMysqlPort() : 3306;
        String db = blankTo(req.getMysqlDatabase(), "show");
        return "jdbc:mysql://" + host + ":" + port + "/" + db
            + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
            + "&allowPublicKeyRetrieval=true&useSSL=false";
    }

    private static java.util.List<Map<String, Object>> skus() {
        Map<String, Object> local = new LinkedHashMap<>();
        local.put("id", "local");
        local.put("name", "本地买断版");
        local.put("licenseModel", "buyout");
        local.put("db", "sqlite");
        local.put("summary", "本机安装，数据存在本机，一次买断，可离线");
        local.put("includes", java.util.List.of("门店收银会员", "本机 SQLite", "离线可用", "文件备份"));
        local.put("excludes", java.util.List.of("云端 MySQL", "SaaS 运营台", "微信小程序"));

        Map<String, Object> saas = new LinkedHashMap<>();
        saas.put("id", "saas");
        saas.put("name", "SaaS 云版");
        saas.put("licenseModel", "subscription");
        saas.put("db", "mysql");
        saas.put("summary", "连接云端/本机 MySQL，订阅制，支持运营台与小程序");
        saas.put("includes", java.util.List.of("云端 MySQL", "SaaS 运营台", "邀请开店", "商家小程序"));
        saas.put("excludes", java.util.List.of("纯离线单机买断体验（请选本地版）"));

        return java.util.List.of(local, saas);
    }

    private static void ensureShowDir() {
        try {
            Files.createDirectories(InstallPaths.showDir());
        } catch (IOException e) {
            throw new IllegalStateException("无法创建目录 " + InstallPaths.showDir());
        }
    }

    private static String blankTo(String v, String def) {
        return v == null || v.isBlank() ? def : v.trim();
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t.getMessage() == null ? e.getMessage() : t.getMessage();
    }
}
