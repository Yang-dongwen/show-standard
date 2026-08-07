package com.ddmo.saas.service;

import com.ddmo.app.util.SnowflakeIdGenerator;
import com.ddmo.saas.security.SaasContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SaasAuditService {

    private final JdbcTemplate jdbcTemplate;
    private final SnowflakeIdGenerator idGenerator;

    public SaasAuditService(JdbcTemplate jdbcTemplate, SnowflakeIdGenerator idGenerator) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = idGenerator;
    }

    public void log(String action, String targetType, String targetId, String detail) {
        String operator = "system";
        try {
            operator = SaasContext.getUsername();
        } catch (Exception ignored) {
            // 公开开店等无 SaaS 登录上下文
        }
        logAs(operator, action, targetType, targetId, detail);
    }

    public void logAs(String operator, String action, String targetType, String targetId, String detail) {
        String op = operator == null || operator.isBlank() ? "system" : operator.trim();
        String d = detail == null ? "" : detail;
        if (d.length() > 500) {
            d = d.substring(0, 500);
        }
        jdbcTemplate.update("""
                INSERT INTO t_saas_audit_log(id, operator, action, target_type, target_id, detail, created_at)
                VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
            idGenerator.nextId(),
            op,
            action == null ? "" : action,
            targetType == null ? "" : targetType,
            targetId == null ? "" : targetId,
            d
        );
    }

    public List<Map<String, Object>> list(int limit) {
        int safe = Math.min(Math.max(limit, 1), 200);
        return jdbcTemplate.queryForList("""
            SELECT id, operator, action, target_type, target_id, detail, created_at
            FROM t_saas_audit_log
            ORDER BY created_at DESC
            LIMIT ?
            """, safe
        ).stream().map(row -> {
            Map<String, Object> m = new HashMap<>(row);
            if (m.get("id") != null) {
                m.put("id", String.valueOf(m.get("id")));
            }
            return m;
        }).collect(Collectors.toList());
    }
}
