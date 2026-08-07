package com.ddmo.saas.service;

import com.ddmo.app.util.SnowflakeIdGenerator;
import com.ddmo.saas.security.SaasContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SaasAnnouncementService {

    private final JdbcTemplate jdbcTemplate;
    private final SnowflakeIdGenerator idGenerator;
    private final SaasAuditService auditService;

    public SaasAnnouncementService(
        JdbcTemplate jdbcTemplate,
        SnowflakeIdGenerator idGenerator,
        SaasAuditService auditService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = idGenerator;
        this.auditService = auditService;
    }

    public List<Map<String, Object>> listAll() {
        return jdbcTemplate.queryForList("""
            SELECT id, title, content, scope, tenant_id, status, created_by, created_at
            FROM t_saas_announcement
            ORDER BY created_at DESC
            LIMIT 100
            """
        ).stream().map(this::stringify).collect(Collectors.toList());
    }

    /**
     * C 端可见：全网 active + 指定本店 active
     */
    public List<Map<String, Object>> listForTenant(long tenantId) {
        return jdbcTemplate.queryForList("""
            SELECT id, title, content, scope, created_at
            FROM t_saas_announcement
            WHERE status = 'active'
              AND (scope = 'all' OR (scope = 'tenant' AND tenant_id = ?))
            ORDER BY created_at DESC
            LIMIT 20
            """, tenantId
        ).stream().map(this::stringify).collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> create(String title, String content, String scope, Long tenantId) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("标题不能为空");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("内容不能为空");
        }
        String sc = scope == null || scope.isBlank() ? "all" : scope.trim();
        if (!"all".equals(sc) && !"tenant".equals(sc)) {
            throw new IllegalArgumentException("scope 仅支持 all / tenant");
        }
        if ("tenant".equals(sc) && (tenantId == null || tenantId <= 0)) {
            throw new IllegalArgumentException("指定门店公告必须传 tenantId");
        }
        String t = title.trim();
        String c = content.trim();
        if (t.length() > 128) {
            throw new IllegalArgumentException("标题过长");
        }
        if (c.length() > 1000) {
            throw new IllegalArgumentException("内容过长");
        }
        long id = idGenerator.nextId();
        String by = "system";
        try {
            by = SaasContext.getUsername();
        } catch (Exception ignored) {
        }
        jdbcTemplate.update("""
                INSERT INTO t_saas_announcement(id, title, content, scope, tenant_id, status, created_by, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'active', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
            id, t, c, sc, "tenant".equals(sc) ? tenantId : null, by
        );
        auditService.log("ANNOUNCE_CREATE", "announcement", String.valueOf(id), t);
        Map<String, Object> m = new HashMap<>();
        m.put("id", String.valueOf(id));
        m.put("title", t);
        m.put("content", c);
        m.put("scope", sc);
        return m;
    }

    @Transactional
    public void revoke(long id) {
        int n = jdbcTemplate.update(
            "UPDATE t_saas_announcement SET status = 'revoked', updated_at = CURRENT_TIMESTAMP WHERE id = ?",
            id
        );
        if (n == 0) {
            throw new IllegalArgumentException("公告不存在");
        }
        auditService.log("ANNOUNCE_REVOKE", "announcement", String.valueOf(id), "吊销公告");
    }

    private Map<String, Object> stringify(Map<String, Object> row) {
        Map<String, Object> m = new HashMap<>(row);
        if (m.get("id") != null) {
            m.put("id", String.valueOf(m.get("id")));
        }
        if (m.get("tenant_id") != null) {
            m.put("tenant_id", String.valueOf(m.get("tenant_id")));
        }
        return m;
    }
}
