package com.ddmo.app.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 租户可访问性：停用 / 到期 / 只读写保护（云版 SaaS 商业化）。
 */
@Service
public class TenantAccessService {

    private final JdbcTemplate jdbcTemplate;

    public TenantAccessService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 登录前：停用或到期不可进 */
    public void assertCanLogin(long tenantId) {
        Map<String, Object> t = loadTenant(tenantId);
        if (t == null) {
            return;
        }
        if (!"active".equals(String.valueOf(t.get("status")))) {
            throw new IllegalArgumentException("门店已停用，请联系平台");
        }
        if (isExpired(t.get("expire_at"))) {
            throw new IllegalArgumentException("套餐已到期，请联系平台续期后再登录");
        }
    }

    /** 业务写入前：停用 / 到期 / 只读 均禁止 */
    public void assertCanWrite(long tenantId) {
        Map<String, Object> t = loadTenant(tenantId);
        if (t == null) {
            return;
        }
        if (!"active".equals(String.valueOf(t.get("status")))) {
            throw new IllegalArgumentException("门店已停用，无法操作");
        }
        if (isExpired(t.get("expire_at"))) {
            throw new IllegalArgumentException("套餐已到期，无法写入数据，请联系平台续期");
        }
        String mode = t.get("write_mode") == null ? "normal" : String.valueOf(t.get("write_mode"));
        if ("readonly".equalsIgnoreCase(mode)) {
            throw new IllegalArgumentException("门店为只读模式，无法新增或修改业务数据");
        }
    }

    public boolean isExpired(Object expireAt) {
        if (expireAt == null) {
            return false;
        }
        try {
            Instant exp;
            if (expireAt instanceof Timestamp ts) {
                exp = ts.toInstant();
            } else if (expireAt instanceof java.util.Date d) {
                exp = d.toInstant();
            } else {
                String s = String.valueOf(expireAt).trim().replace('T', ' ');
                if (s.length() >= 19) {
                    s = s.substring(0, 19);
                }
                exp = Timestamp.valueOf(s).toInstant();
            }
            return Instant.now().isAfter(exp);
        } catch (Exception e) {
            return false;
        }
    }

    private Map<String, Object> loadTenant(long tenantId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT status, expire_at, write_mode FROM t_tenant WHERE id = ?",
            tenantId
        );
        return rows.isEmpty() ? null : rows.get(0);
    }
}
