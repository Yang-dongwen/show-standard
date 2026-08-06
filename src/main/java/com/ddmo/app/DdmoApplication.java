package com.ddmo.app;

import com.ddmo.app.desktop.DesktopSupport;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Show 后端入口：标准 Spring Boot Web 服务。
 * <p>
 * 桌面模式（仅当 {@code -Ddesktop.mode=true} 时启用）：
 * headless=false、单实例、系统托盘退出、就绪后打开默认浏览器。
 * 正常 Web 启动不受影响。
 */
@SpringBootApplication
public class DdmoApplication {

    public static void main(String[] args) {
        ensureSqliteDirAndPendingRestore();

        boolean desktopMode = Boolean.parseBoolean(System.getProperty("desktop.mode", "false"));
        if (desktopMode) {
            System.setProperty("java.awt.headless", "false");
            int port = DesktopSupport.resolvePort(args);
            // 已在运行则只打开浏览器并退出，避免重复启动导致 Failed to launch JVM
            if (!DesktopSupport.claimOrHandoff(port)) {
                return;
            }
        }

        SpringApplication app = new SpringApplication(DdmoApplication.class);
        if (desktopMode) {
            app.addListeners((ApplicationListener<ApplicationReadyEvent>) event -> {
                Environment env = event.getApplicationContext().getEnvironment();
                String port = env.getProperty("local.server.port");
                if (port == null || port.isBlank()) {
                    port = env.getProperty("server.port", "8080");
                }
                DesktopSupport.onServerReady("http://localhost:" + port);
            });
        }
        app.run(args);
    }

    /**
     * 创建数据目录；若存在 restore-pending.db，在打开数据源之前替换主库。
     */
    private static void ensureSqliteDirAndPendingRestore() {
        try {
            Path dir = Path.of(System.getProperty("user.home"), ".show");
            Files.createDirectories(dir);

            Path pending = dir.resolve("restore-pending.db");
            Path db = dir.resolve("show.db");
            if (Files.exists(pending)) {
                if (Files.exists(db)) {
                    String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
                    Files.copy(db, dir.resolve("show.db.before-restore-" + stamp), StandardCopyOption.REPLACE_EXISTING);
                }
                Files.deleteIfExists(dir.resolve("show.db-wal"));
                Files.deleteIfExists(dir.resolve("show.db-shm"));
                Files.move(pending, db, StandardCopyOption.REPLACE_EXISTING);
                Files.deleteIfExists(dir.resolve("show.db-wal"));
                Files.deleteIfExists(dir.resolve("show.db-shm"));
            }
        } catch (Exception e) {
            throw new IllegalStateException("创建/恢复 SQLite 目录失败", e);
        }
    }
}
