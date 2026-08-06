package com.ddmo.app.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * SQLite 运行时 PRAGMA：WAL、外键、busy_timeout。
 * 全局 lazy-init 下必须 eager，否则 PRAGMA 可能永远不执行。
 */
@Component
@Lazy(false)
public class SqliteConfigurer {

    private static final Logger log = LoggerFactory.getLogger(SqliteConfigurer.class);

    private final JdbcTemplate jdbcTemplate;

    public SqliteConfigurer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void configure() {
        jdbcTemplate.execute("PRAGMA journal_mode=WAL");
        jdbcTemplate.execute("PRAGMA foreign_keys=ON");
        jdbcTemplate.execute("PRAGMA busy_timeout=5000");
        jdbcTemplate.execute("PRAGMA synchronous=NORMAL");
        log.info("SQLite PRAGMA 已应用: WAL + foreign_keys + busy_timeout");
    }
}
