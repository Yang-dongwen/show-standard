package com.ddmo.app.config;

import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 本地 SQLite 时确保 ~/.show 目录存在；云版 MySQL 不创建文件库目录。
 */
@Component
public class SqlitePathInitializer {

    private final Environment environment;

    public SqlitePathInitializer(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void init() {
        String url = environment.getProperty("spring.datasource.url", "");
        if (url == null || !url.toLowerCase().contains("jdbc:sqlite:")) {
            return;
        }
        try {
            Path dir = Path.of(System.getProperty("user.home"), ".show");
            Files.createDirectories(dir);
        } catch (Exception e) {
            throw new IllegalStateException("创建 SQLite 目录失败", e);
        }
    }
}

