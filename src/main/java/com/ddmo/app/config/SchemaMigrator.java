package com.ddmo.app.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 兼容已有库的增量迁移（CREATE IF NOT EXISTS 之外的列/索引/数据回填）。
 * 全局 lazy-initialization=true 时必须 @Lazy(false)，否则无人注入则永不执行。
 */
@Component
@Lazy(false)
@Order(100)
public class SchemaMigrator {

    private static final Logger log = LoggerFactory.getLogger(SchemaMigrator.class);
    private static final String SCHEMA_VERSION = "2";

    private final JdbcTemplate jdbcTemplate;

    public SchemaMigrator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void migrate() {
        ensureColumn("t_recharge_record", "status", "VARCHAR(16) NOT NULL DEFAULT 'normal'");
        ensureColumn("t_recharge_record", "related_id", "BIGINT");
        ensureColumn("t_consume_record", "status", "VARCHAR(16) NOT NULL DEFAULT 'normal'");
        ensureColumn("t_consume_record", "related_id", "BIGINT");

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS t_account (
                customer_id BIGINT PRIMARY KEY,
                tenant_id BIGINT NOT NULL,
                balance NUMERIC(12, 2) NOT NULL DEFAULT 0,
                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """);
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_account_tenant ON t_account(tenant_id)");

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS t_tenant_setting (
                tenant_id BIGINT NOT NULL,
                setting_key VARCHAR(64) NOT NULL,
                setting_value VARCHAR(500) NOT NULL DEFAULT '',
                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (tenant_id, setting_key)
            )
            """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS t_schema_meta (
                meta_key VARCHAR(64) PRIMARY KEY,
                meta_value VARCHAR(255) NOT NULL,
                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """);

        // 用户名全局唯一（登录按全局 username）
        try {
            jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_manager_username ON t_manager(username)");
        } catch (Exception e) {
            log.warn("创建 username 唯一索引失败（可能已有重复用户名）: {}", e.getMessage());
        }

        backfillAccounts();
        upsertMeta("schema_version", SCHEMA_VERSION);
        log.info("Schema 迁移完成, version={}", SCHEMA_VERSION);
    }

    private void ensureColumn(String table, String column, String definition) {
        if (columnExists(table, column)) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        log.info("已添加列 {}.{}", table, column);
    }

    private boolean columnExists(String table, String column) {
        // table/column 名仅来自本类常量，禁止外部输入
        List<Map<String, Object>> cols = jdbcTemplate.queryForList("PRAGMA table_info(" + table + ")");
        for (Map<String, Object> col : cols) {
            if (column.equalsIgnoreCase(String.valueOf(col.get("name")))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 为缺失账户的会员回填余额 = SUM(normal 充值) - SUM(normal 消费)。
     */
    private void backfillAccounts() {
        List<Map<String, Object>> customers = jdbcTemplate.queryForList(
            "SELECT id, tenant_id FROM t_customer"
        );
        int created = 0;
        for (Map<String, Object> row : customers) {
            long customerId = ((Number) row.get("id")).longValue();
            long tenantId = ((Number) row.get("tenant_id")).longValue();
            Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM t_account WHERE customer_id = ?",
                Integer.class,
                customerId
            );
            if (exists != null && exists > 0) {
                continue;
            }
            BigDecimal recharge = jdbcTemplate.queryForObject(
                """
                    SELECT COALESCE(SUM(amount),0) FROM t_recharge_record
                    WHERE tenant_id = ? AND customer_id = ? AND COALESCE(status,'normal') = 'normal'
                    """,
                BigDecimal.class, tenantId, customerId
            );
            BigDecimal consume = jdbcTemplate.queryForObject(
                """
                    SELECT COALESCE(SUM(amount),0) FROM t_consume_record
                    WHERE tenant_id = ? AND customer_id = ? AND COALESCE(status,'normal') = 'normal'
                    """,
                BigDecimal.class, tenantId, customerId
            );
            BigDecimal balance = (recharge == null ? BigDecimal.ZERO : recharge)
                .subtract(consume == null ? BigDecimal.ZERO : consume);
            jdbcTemplate.update(
                """
                    INSERT INTO t_account(customer_id, tenant_id, balance, updated_at)
                    VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                    """,
                customerId, tenantId, balance
            );
            created++;
        }
        if (created > 0) {
            log.info("已回填 {} 个会员账户余额", created);
        }
    }

    private void upsertMeta(String key, String value) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM t_schema_meta WHERE meta_key = ?",
            Integer.class,
            key
        );
        if (count != null && count > 0) {
            jdbcTemplate.update(
                "UPDATE t_schema_meta SET meta_value = ?, updated_at = CURRENT_TIMESTAMP WHERE meta_key = ?",
                value, key
            );
        } else {
            jdbcTemplate.update(
                "INSERT INTO t_schema_meta(meta_key, meta_value, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP)",
                key, value
            );
        }
    }
}
