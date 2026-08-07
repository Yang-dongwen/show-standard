package com.ddmo.app.install;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 启动最早阶段按「安装向导」结果切换数据源。
 * <ul>
 *   <li>已完成 install：local → SQLite；saas → MySQL</li>
 *   <li>未完成 + 桌面壳（desktop.mode）：临时 SQLite 仅用于拉起安装向导（不代表已选买断）</li>
 *   <li>未完成 + 普通开发启动：不覆盖，沿用 application.yml 默认 cloud/MySQL</li>
 * </ul>
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class InstallConfigEnvironmentPostProcessor implements EnvironmentPostProcessor {

    public static final String PROPERTY_SOURCE_NAME = "showInstallConfig";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Properties props = loadInstallProps();
        boolean completed = props != null
            && "true".equalsIgnoreCase(props.getProperty("install.completed", ""));

        if (completed) {
            applyCompletedInstall(environment, props);
            return;
        }

        // 桌面安装包首次启动：尚无选型时用 SQLite 起进程，才能打开安装向导
        if (isDesktopShell()) {
            Map<String, Object> map = new HashMap<>();
            applySqlite(map);
            map.put("install.bootstrap", "true");
            map.put("install.completed", "false");
            // 向导未完成前不当成买断正式版（SaaS 门禁仍按 deployment=desktop 关运营台，避免半安装误用）
            map.put("app.deployment", "desktop");
            environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, map));
        }
        // 日常 mvn 启动：无 install 或未完成时不覆盖，默认 cloud + MySQL
    }

    private void applyCompletedInstall(ConfigurableEnvironment environment, Properties props) {
        Map<String, Object> map = new HashMap<>();
        String edition = props.getProperty("install.edition", "local").trim().toLowerCase();
        if ("saas".equals(edition) || "cloud".equals(edition)) {
            map.put("app.deployment", "cloud");
            map.put("spring.profiles.active", "cloud");
            map.put("spring.flyway.locations", "classpath:db/migration/mysql");
            putIfPresent(map, "spring.datasource.url", props.getProperty("spring.datasource.url"));
            putIfPresent(map, "spring.datasource.username", props.getProperty("spring.datasource.username"));
            putIfPresent(map, "spring.datasource.password", props.getProperty("spring.datasource.password"));
            map.putIfAbsent("spring.datasource.driver-class-name", "com.mysql.cj.jdbc.Driver");
            map.put("spring.datasource.hikari.maximum-pool-size", "10");
            map.put("spring.datasource.hikari.minimum-idle", "2");
            map.put("spring.datasource.hikari.pool-name", "show-mysql");
            map.put("app.wx.miniapp.enabled", props.getProperty("app.wx.miniapp.enabled", "true"));
            map.put("install.edition", "saas");
        } else {
            // 仅安装向导选择「本地买断」后才固定 SQLite
            map.put("app.deployment", "desktop");
            map.put("spring.profiles.active", "desktop");
            applySqlite(map);
            map.put("app.wx.miniapp.enabled", "false");
            map.put("install.edition", "local");
        }
        map.put("install.completed", "true");
        map.put("install.bootstrap", "false");
        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, map));
    }

    private static void applySqlite(Map<String, Object> map) {
        map.put("spring.profiles.active", "desktop");
        map.put("spring.datasource.url", "jdbc:sqlite:" + InstallPaths.sqliteDb().toAbsolutePath());
        map.put("spring.datasource.driver-class-name", "org.sqlite.JDBC");
        map.put("spring.datasource.username", "show");
        map.put("spring.datasource.password", "show");
        map.put("spring.flyway.locations", "classpath:db/migration/sqlite");
        map.put("spring.datasource.hikari.maximum-pool-size", "1");
        map.put("spring.datasource.hikari.minimum-idle", "1");
        map.put("spring.datasource.hikari.pool-name", "show-sqlite");
        map.put("spring.datasource.hikari.max-lifetime", "0");
    }

    private static boolean isDesktopShell() {
        return Boolean.parseBoolean(System.getProperty("desktop.mode", "false"));
    }

    private static Properties loadInstallProps() {
        var file = InstallPaths.installFile();
        if (!Files.isRegularFile(file)) {
            return null;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
            return props;
        } catch (IOException e) {
            throw new IllegalStateException("读取安装配置失败: " + file, e);
        }
    }

    private static void putIfPresent(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value.trim());
        }
    }
}
