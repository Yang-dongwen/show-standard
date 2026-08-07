package com.ddmo.app.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * SQLite 运行时 PRAGMA：WAL、外键、busy_timeout。
 * 仅在 JDBC URL 为 sqlite 时执行；cloud / MySQL 时跳过。
 */
@Component
@Lazy(false)
public class SqliteConfigurer {

    private static final Logger log = LoggerFactory.getLogger(SqliteConfigurer.class);

    private final JdbcTemplate jdbcTemplate;
    private final Environment environment;

    public SqliteConfigurer(JdbcTemplate jdbcTemplate, Environment environment) {
        this.jdbcTemplate = jdbcTemplate;
        this.environment = environment;
    }

    @PostConstruct
    public void configure() {
        String url = environment.getProperty("spring.datasource.url", "");
        if (url == null || !url.toLowerCase().contains("sqlite")) {
            log.info("非 SQLite 数据源，跳过 PRAGMA");
            return;
        }
        jdbcTemplate.execute("PRAGMA journal_mode=WAL");
        jdbcTemplate.execute("PRAGMA foreign_keys=ON");
        jdbcTemplate.execute("PRAGMA busy_timeout=5000");
        jdbcTemplate.execute("PRAGMA synchronous=NORMAL");
        log.info("SQLite PRAGMA 已应用: WAL + foreign_keys + busy_timeout");
    }
}
