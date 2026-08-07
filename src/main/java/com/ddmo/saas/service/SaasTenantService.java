package com.ddmo.saas.service;

import com.ddmo.app.config.DbDialect;
import com.ddmo.app.util.SnowflakeIdGenerator;
import com.ddmo.saas.config.AppSaasProperties;
import com.ddmo.saas.dto.RegisterShopRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SaaS 租户生命周期 + 对 C 端业务表的只读汇总 + 代运营。
 */
@Service
public class SaasTenantService {

    private static final String KEY_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";

    private final JdbcTemplate jdbcTemplate;
    private final SnowflakeIdGenerator idGenerator;
    private final SaasInviteService inviteService;
    private final AppSaasProperties saasProperties;
    private final SaasAuditService auditService;
    private final SaasPlanService planService;
    private final DbDialect dbDialect;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecureRandom secureRandom = new SecureRandom();

    public SaasTenantService(
        JdbcTemplate jdbcTemplate,
        SnowflakeIdGenerator idGenerator,
        SaasInviteService inviteService,
        AppSaasProperties saasProperties,
        SaasAuditService auditService,
        SaasPlanService planService,
        DbDialect dbDialect
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = idGenerator;
        this.inviteService = inviteService;
        this.saasProperties = saasProperties;
        this.auditService = auditService;
        this.planService = planService;
        this.dbDialect = dbDialect;
    }

    public List<Map<String, Object>> listTenants() {
        return jdbcTemplate.queryForList("""
            SELECT t.id, t.tenant_key, t.shop_name, t.status, t.plan_code,
                   t.max_customers, t.max_employees, t.tags, t.remark, t.expire_at, t.write_mode, t.created_at,
                   m.username AS manager_username, m.nickname AS manager_nickname
            FROM t_tenant t
            LEFT JOIN t_manager m ON m.tenant_id = t.id AND COALESCE(m.role, 'owner') = 'owner'
            ORDER BY t.created_at DESC
            """
        ).stream().map(this::stringifyId).collect(Collectors.toList());
    }

    public Map<String, Object> tenantDetail(long tenantId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT t.id, t.tenant_key, t.shop_name, t.status, t.plan_code,
                   t.max_customers, t.max_employees, t.tags, t.remark, t.expire_at, t.write_mode, t.created_at,
                   m.username AS manager_username, m.nickname AS manager_nickname
            FROM t_tenant t
            LEFT JOIN t_manager m ON m.tenant_id = t.id AND COALESCE(m.role, 'owner') = 'owner'
            WHERE t.id = ?
            """, tenantId);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("租户不存在");
        }
        Map<String, Object> detail = stringifyId(rows.get(0));

        Long customers = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM t_customer WHERE tenant_id = ? AND status = 'active'",
            Long.class, tenantId
        );
        Long employees = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM t_employee WHERE tenant_id = ? AND status = 'active'",
            Long.class, tenantId
        );
        BigDecimal balanceSum = jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(balance),0) FROM t_account WHERE tenant_id = ?",
            BigDecimal.class, tenantId
        );
        BigDecimal recharge = jdbcTemplate.queryForObject(
            """
                SELECT COALESCE(SUM(amount),0) FROM t_recharge_record
                WHERE tenant_id = ? AND COALESCE(status,'normal') = 'normal'
                """,
            BigDecimal.class, tenantId
        );
        BigDecimal consume = jdbcTemplate.queryForObject(
            """
                SELECT COALESCE(SUM(amount),0) FROM t_consume_record
                WHERE tenant_id = ? AND COALESCE(status,'normal') = 'normal'
                """,
            BigDecimal.class, tenantId
        );

        Map<String, Object> stats = new HashMap<>();
        stats.put("activeCustomers", customers == null ? 0 : customers);
        stats.put("activeEmployees", employees == null ? 0 : employees);
        stats.put("totalBalance", balanceSum == null ? BigDecimal.ZERO : balanceSum);
        stats.put("totalRecharge", recharge == null ? BigDecimal.ZERO : recharge);
        stats.put("totalConsume", consume == null ? BigDecimal.ZERO : consume);
        detail.put("cendStats", stats);

        // 近 7 日趋势（SQLite / MySQL 方言）
        List<Map<String, Object>> trend = new java.util.ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            String dayExpr = dbDialect.dateOffsetExpr(-i);
            Map<String, Object> day = new HashMap<>();
            day.put("day", jdbcTemplate.queryForObject("SELECT " + dayExpr, String.class));
            day.put("recharge", jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(amount),0) FROM t_recharge_record WHERE tenant_id=? AND COALESCE(status,'normal')='normal' AND DATE(created_at)=" + dayExpr,
                BigDecimal.class, tenantId
            ));
            day.put("consume", jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(amount),0) FROM t_consume_record WHERE tenant_id=? AND COALESCE(status,'normal')='normal' AND DATE(created_at)=" + dayExpr,
                BigDecimal.class, tenantId
            ));
            trend.add(day);
        }
        detail.put("last7Days", trend);
        return detail;
    }

    @Transactional
    public void setTenantStatus(long tenantId, String status) {
        if (!"active".equals(status) && !"suspended".equals(status)) {
            throw new IllegalArgumentException("状态仅支持 active / suspended");
        }
        int n = jdbcTemplate.update(
            "UPDATE t_tenant SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
            status, tenantId
        );
        if (n == 0) {
            throw new IllegalArgumentException("租户不存在");
        }
        auditService.log(
            "active".equals(status) ? "TENANT_ACTIVATE" : "TENANT_SUSPEND",
            "tenant",
            String.valueOf(tenantId),
            "status=" + status
        );
    }

    @Transactional
    public Map<String, Object> updatePlan(long tenantId, String planCode, Integer maxCustomers, Integer maxEmployees) {
        ensureTenant(tenantId);
        String plan = planCode == null || planCode.isBlank() ? "free" : planCode.trim();
        int maxC;
        int maxE;
        // 若套餐目录存在则优先用目录配额
        try {
            Map<String, Object> catalog = planService.getByCode(plan);
            maxC = maxCustomers != null ? maxCustomers : ((Number) catalog.get("max_customers")).intValue();
            maxE = maxEmployees != null ? maxEmployees : ((Number) catalog.get("max_employees")).intValue();
        } catch (IllegalArgumentException ex) {
            maxC = maxCustomers == null ? 5000 : maxCustomers;
            maxE = maxEmployees == null ? 50 : maxEmployees;
        }
        if (maxC < 1 || maxE < 1) {
            throw new IllegalArgumentException("配额必须大于 0");
        }
        jdbcTemplate.update("""
                UPDATE t_tenant
                SET plan_code = ?, max_customers = ?, max_employees = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
            plan, maxC, maxE, tenantId
        );
        auditService.log("TENANT_PLAN", "tenant", String.valueOf(tenantId),
            "plan=" + plan + ", maxC=" + maxC + ", maxE=" + maxE);
        return tenantDetail(tenantId);
    }

    @Transactional
    public Map<String, Object> applyCatalogPlan(long tenantId, String planCode) {
        Map<String, Object> plan = planService.getByCode(planCode);
        int maxC = ((Number) plan.get("max_customers")).intValue();
        int maxE = ((Number) plan.get("max_employees")).intValue();
        int trial = ((Number) plan.get("trial_days")).intValue();
        jdbcTemplate.update("""
                UPDATE t_tenant
                SET plan_code = ?, max_customers = ?, max_employees = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
            plan.get("code"), maxC, maxE, tenantId
        );
        if (trial > 0) {
            Timestamp exp = Timestamp.from(Instant.now().plusSeconds(trial * 86400L));
            jdbcTemplate.update(
                "UPDATE t_tenant SET expire_at = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                exp, tenantId
            );
        }
        auditService.log("TENANT_APPLY_PLAN", "tenant", String.valueOf(tenantId),
            "apply plan " + planCode);
        return tenantDetail(tenantId);
    }

    @Transactional
    public Map<String, Object> updateMeta(long tenantId, String tags, String remark, Integer expireDays) {
        ensureTenant(tenantId);
        String t = tags == null ? "" : tags.trim();
        String r = remark == null ? "" : remark.trim();
        if (t.length() > 255) {
            throw new IllegalArgumentException("标签过长");
        }
        if (r.length() > 500) {
            throw new IllegalArgumentException("备注过长");
        }
        jdbcTemplate.update(
            "UPDATE t_tenant SET tags = ?, remark = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
            t, r, tenantId
        );
        if (expireDays != null) {
            if (expireDays <= 0) {
                jdbcTemplate.update(
                    "UPDATE t_tenant SET expire_at = NULL, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                    tenantId
                );
            } else {
                Timestamp exp = Timestamp.from(Instant.now().plusSeconds(expireDays * 86400L));
                jdbcTemplate.update(
                    "UPDATE t_tenant SET expire_at = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                    exp, tenantId
                );
            }
        }
        auditService.log("TENANT_META", "tenant", String.valueOf(tenantId), "更新标签/备注/到期");
        return tenantDetail(tenantId);
    }

    @Transactional
    public void resetManagerPassword(long tenantId, String newPassword) {
        if (newPassword == null || newPassword.trim().length() < 6) {
            throw new IllegalArgumentException("新密码至少6位");
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
                SELECT id, username FROM t_manager
                WHERE tenant_id = ?
                ORDER BY CASE COALESCE(role,'owner') WHEN 'owner' THEN 0 ELSE 1 END, id ASC
                LIMIT 1
                """,
            tenantId
        );
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("该门店无店长账号");
        }
        long managerId = ((Number) rows.get(0).get("id")).longValue();
        String username = String.valueOf(rows.get(0).get("username"));
        jdbcTemplate.update(
            "UPDATE t_manager SET password_hash = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
            passwordEncoder.encode(newPassword.trim()), managerId
        );
        auditService.log("RESET_MANAGER_PWD", "tenant", String.valueOf(tenantId),
            "reset password for " + username);
    }

    @Transactional
    public Map<String, Object> registerShop(RegisterShopRequest request) {
        if (request.getUsername() == null || request.getUsername().isBlank()
            || request.getPassword() == null || request.getPassword().isBlank()
            || request.getNickname() == null || request.getNickname().isBlank()) {
            throw new IllegalArgumentException("用户名、密码、昵称不能为空");
        }
        long inviteId = inviteService.assertUsable(request.getInviteCode(), saasProperties.getInviteCode());

        String username = request.getUsername().trim();
        String password = request.getPassword().trim();
        String nickname = request.getNickname().trim();
        if (password.length() < 6) {
            throw new IllegalArgumentException("密码长度不能少于6位");
        }
        if (nickname.length() > 6) {
            throw new IllegalArgumentException("昵称最多6个字");
        }
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM t_manager WHERE username = ?", Long.class, username
        );
        if (count != null && count > 0) {
            throw new IllegalArgumentException("用户名已存在");
        }

        String shopName = request.getShopName() == null ? "" : request.getShopName().trim();
        if (shopName.isEmpty()) {
            shopName = nickname + "的理发店";
        }
        if (shopName.length() > 64) {
            throw new IllegalArgumentException("门店名称过长");
        }

        long tenantId = idGenerator.nextId();
        long managerId = idGenerator.nextId();
        String tenantKey = generateTenantKey();

        int maxC = 500;
        int maxE = 5;
        try {
            Map<String, Object> free = planService.getByCode("free");
            maxC = ((Number) free.get("max_customers")).intValue();
            maxE = ((Number) free.get("max_employees")).intValue();
        } catch (Exception ignored) {
        }

        jdbcTemplate.update("""
                INSERT INTO t_tenant(id, tenant_key, shop_name, status, plan_code,
                    max_customers, max_employees, tags, remark, created_at, updated_at)
                VALUES (?, ?, ?, 'active', 'free', ?, ?, '', '', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
            tenantId, tenantKey, shopName, maxC, maxE);

        jdbcTemplate.update("""
                INSERT INTO t_manager(id, tenant_id, username, password_hash, nickname, role, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'owner', 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
            managerId, tenantId, username, passwordEncoder.encode(password), nickname);

        inviteService.consume(inviteId);
        insertDefaultServiceTypes(tenantId);
        auditService.logAs("public", "SHOP_REGISTER", "tenant", String.valueOf(tenantId),
            "open shop " + shopName + " by " + username);

        Map<String, Object> result = new HashMap<>();
        result.put("tenantId", String.valueOf(tenantId));
        result.put("tenantKey", tenantKey);
        result.put("shopName", shopName);
        result.put("username", username);
        result.put("hint", "请使用 C 端（Show 门店端）登录收银");
        return result;
    }

    public Map<String, Object> publicRegisterStatus() {
        Map<String, Object> data = new HashMap<>();
        data.put("requireInviteCode", true);
        data.put("allowed", true);
        data.put("system", "saas");
        return data;
    }

    private void ensureTenant(long tenantId) {
        Integer exists = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM t_tenant WHERE id = ?", Integer.class, tenantId
        );
        if (exists == null || exists == 0) {
            throw new IllegalArgumentException("租户不存在");
        }
    }

    private void insertDefaultServiceTypes(long tenantId) {
        insertService(tenantId, "洗剪吹", BigDecimal.valueOf(58));
        insertService(tenantId, "染发", BigDecimal.valueOf(188));
        insertService(tenantId, "烫发", BigDecimal.valueOf(268));
        insertService(tenantId, "护理", BigDecimal.valueOf(128));
    }

    private void insertService(long tenantId, String name, BigDecimal price) {
        jdbcTemplate.update("""
                INSERT INTO t_service_type(id, tenant_id, name, price, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
            idGenerator.nextId(), tenantId, name, price);
    }

    private String generateTenantKey() {
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder sb = new StringBuilder(8);
            for (int i = 0; i < 8; i++) {
                sb.append(KEY_CHARS.charAt(secureRandom.nextInt(KEY_CHARS.length())));
            }
            String key = sb.toString();
            Integer c = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM t_tenant WHERE tenant_key = ?", Integer.class, key
            );
            if (c == null || c == 0) {
                return key;
            }
        }
        return "t" + Long.toString(idGenerator.nextId(), 36);
    }

    private Map<String, Object> stringifyId(Map<String, Object> row) {
        Map<String, Object> m = new HashMap<>(row);
        if (m.get("id") != null) {
            m.put("id", String.valueOf(m.get("id")));
        }
        return m;
    }
}
