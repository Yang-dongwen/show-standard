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
 * 启动最早阶段加载 ~/.show/install.properties，使数据源 / deployment / profile 按安装向导生效。
 * 优先级高于 application.yml 与部分 -D 默认值（addFirst）。
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class InstallConfigEnvironmentPostProcessor implements EnvironmentPostProcessor {

    public static final String PROPERTY_SOURCE_NAME = "showInstallConfig";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        var file = InstallPaths.installFile();
        if (!Files.isRegularFile(file)) {
            return;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("读取安装配置失败: " + file, e);
        }
        if (!"true".equalsIgnoreCase(props.getProperty("install.completed", ""))) {
            return;
        }

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
            // 避免 cloud yml 被盖掉后仍用 sqlite pool 名
            map.put("spring.datasource.hikari.maximum-pool-size", "10");
            map.put("spring.datasource.hikari.minimum-idle", "2");
            map.put("spring.datasource.hikari.pool-name", "show-mysql");
            map.put("app.wx.miniapp.enabled", props.getProperty("app.wx.miniapp.enabled", "true"));
        } else {
            map.put("app.deployment", "desktop");
            // 固定本地 SQLite，并避免误留 cloud profile
            map.put("spring.profiles.active", "");
            map.put("spring.datasource.url", "jdbc:sqlite:" + InstallPaths.sqliteDb().toAbsolutePath());
            map.put("spring.datasource.driver-class-name", "org.sqlite.JDBC");
            map.put("spring.datasource.username", "show");
            map.put("spring.datasource.password", "show");
            map.put("spring.flyway.locations", "classpath:db/migration/sqlite");
            map.put("spring.datasource.hikari.maximum-pool-size", "1");
            map.put("spring.datasource.hikari.minimum-idle", "1");
            map.put("spring.datasource.hikari.pool-name", "show-sqlite");
            map.put("spring.datasource.hikari.max-lifetime", "0");
            map.put("app.wx.miniapp.enabled", "false");
        }
        map.put("install.completed", "true");
        map.put("install.edition", "saas".equals(edition) || "cloud".equals(edition) ? "saas" : "local");

        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, map));
    }

    private static void putIfPresent(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value.trim());
        }
    }
}
