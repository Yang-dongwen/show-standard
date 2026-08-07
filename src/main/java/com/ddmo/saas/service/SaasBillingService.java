package com.ddmo.saas.service;

import com.ddmo.app.util.SnowflakeIdGenerator;
import com.ddmo.saas.dto.RenewTenantRequest;
import com.ddmo.saas.security.SaasContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SaasBillingService {

    private final JdbcTemplate jdbcTemplate;
    private final SnowflakeIdGenerator idGenerator;
    private final SaasAuditService auditService;
    private final SaasPlanService planService;
    private final SaasTenantService tenantService;

    public SaasBillingService(
        JdbcTemplate jdbcTemplate,
        SnowflakeIdGenerator idGenerator,
        SaasAuditService auditService,
        SaasPlanService planService,
        SaasTenantService tenantService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = idGenerator;
        this.auditService = auditService;
        this.planService = planService;
        this.tenantService = tenantService;
    }

    public List<Map<String, Object>> listAll(int limit) {
        int safe = Math.min(Math.max(limit, 1), 200);
        return jdbcTemplate.queryForList("""
            SELECT b.id, b.tenant_id, t.shop_name, b.plan_code, b.days, b.amount, b.note, b.operator, b.created_at
            FROM t_saas_billing_record b
            LEFT JOIN t_tenant t ON t.id = b.tenant_id
            ORDER BY b.created_at DESC
            LIMIT ?
            """, safe
        ).stream().map(this::stringify).collect(Collectors.toList());
    }

    public List<Map<String, Object>> listByTenant(long tenantId) {
        return jdbcTemplate.queryForList("""
            SELECT id, tenant_id, plan_code, days, amount, note, operator, created_at
            FROM t_saas_billing_record
            WHERE tenant_id = ?
            ORDER BY created_at DESC
            LIMIT 50
            """, tenantId
        ).stream().map(this::stringify).collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> renew(long tenantId, RenewTenantRequest request) {
        if (request == null || request.getDays() == null || request.getDays() < 1) {
            throw new IllegalArgumentException("续期天数必须 >= 1");
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT id, plan_code, expire_at FROM t_tenant WHERE id = ?", tenantId
        );
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("租户不存在");
        }
        Map<String, Object> t = rows.get(0);
        int days = request.getDays();
        String plan = request.getPlanCode() == null || request.getPlanCode().isBlank()
            ? String.valueOf(t.get("plan_code"))
            : request.getPlanCode().trim();

        // 可选套用套餐配额
        try {
            Map<String, Object> catalog = planService.getByCode(plan);
            int maxC = ((Number) catalog.get("max_customers")).intValue();
            int maxE = ((Number) catalog.get("max_employees")).intValue();
            jdbcTemplate.update(
                "UPDATE t_tenant SET plan_code = ?, max_customers = ?, max_employees = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                plan, maxC, maxE, tenantId
            );
        } catch (IllegalArgumentException ignored) {
            jdbcTemplate.update(
                "UPDATE t_tenant SET plan_code = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                plan, tenantId
            );
        }

        Instant base = Instant.now();
        Object exp = t.get("expire_at");
        if (exp instanceof Timestamp ts && ts.toInstant().isAfter(Instant.now())) {
            base = ts.toInstant();
        }
        Timestamp newExp = Timestamp.from(base.plusSeconds(days * 86400L));
        boolean clearRo = request.getClearReadonly() == null || request.getClearReadonly();
        if (clearRo) {
            jdbcTemplate.update(
                "UPDATE t_tenant SET expire_at = ?, write_mode = 'normal', status = 'active', updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                newExp, tenantId
            );
        } else {
            jdbcTemplate.update(
                "UPDATE t_tenant SET expire_at = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                newExp, tenantId
            );
        }

        BigDecimal amount = request.getAmount() == null ? BigDecimal.ZERO : request.getAmount();
        String note = request.getNote() == null ? "" : request.getNote().trim();
        String op = "system";
        try {
            op = SaasContext.getUsername();
        } catch (Exception ignored) {
        }
        long billId = idGenerator.nextId();
        jdbcTemplate.update("""
                INSERT INTO t_saas_billing_record(id, tenant_id, plan_code, days, amount, note, operator, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
            billId, tenantId, plan, days, amount, note, op
        );
        auditService.log("TENANT_RENEW", "tenant", String.valueOf(tenantId),
            "days=" + days + ", plan=" + plan + ", amount=" + amount);

        Map<String, Object> detail = tenantService.tenantDetail(tenantId);
        detail.put("billingId", String.valueOf(billId));
        detail.put("newExpireAt", newExp);
        return detail;
    }

    @Transactional
    public Map<String, Object> setWriteMode(long tenantId, String writeMode) {
        if (!"normal".equals(writeMode) && !"readonly".equals(writeMode)) {
            throw new IllegalArgumentException("writeMode 仅支持 normal / readonly");
        }
        int n = jdbcTemplate.update(
            "UPDATE t_tenant SET write_mode = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
            writeMode, tenantId
        );
        if (n == 0) {
            throw new IllegalArgumentException("租户不存在");
        }
        auditService.log("TENANT_WRITE_MODE", "tenant", String.valueOf(tenantId), "mode=" + writeMode);
        return tenantService.tenantDetail(tenantId);
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
