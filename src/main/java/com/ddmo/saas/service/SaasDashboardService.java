package com.ddmo.saas.service;

import com.ddmo.app.config.DbDialect;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SaasDashboardService {

    private final JdbcTemplate jdbcTemplate;
    private final DbDialect dbDialect;

    public SaasDashboardService(JdbcTemplate jdbcTemplate, DbDialect dbDialect) {
        this.jdbcTemplate = jdbcTemplate;
        this.dbDialect = dbDialect;
    }

    public Map<String, Object> overview() {
        String today = dbDialect.todayExpr();
        Map<String, Object> map = new HashMap<>();
        map.put("tenantTotal", scalarLong("SELECT COUNT(1) FROM t_tenant"));
        map.put("tenantActive", scalarLong("SELECT COUNT(1) FROM t_tenant WHERE status = 'active'"));
        map.put("tenantSuspended", scalarLong("SELECT COUNT(1) FROM t_tenant WHERE status = 'suspended'"));
        map.put("todayNewTenants", scalarLong(
            "SELECT COUNT(1) FROM t_tenant WHERE DATE(created_at) = " + today
        ));
        map.put("todayRecharge", scalarDecimal("""
            SELECT COALESCE(SUM(amount),0) FROM t_recharge_record
            WHERE COALESCE(status,'normal') = 'normal'
              AND DATE(created_at) = %s
            """.formatted(today)));
        map.put("todayConsume", scalarDecimal("""
            SELECT COALESCE(SUM(amount),0) FROM t_consume_record
            WHERE COALESCE(status,'normal') = 'normal'
              AND DATE(created_at) = %s
            """.formatted(today)));
        map.put("totalCustomers", scalarLong("SELECT COUNT(1) FROM t_customer WHERE status = 'active'"));
        map.put("totalBalance", scalarDecimal("SELECT COALESCE(SUM(balance),0) FROM t_account"));
        map.put("inviteActive", scalarLong(
            "SELECT COUNT(1) FROM t_invite_code WHERE status = 'active'"
        ));

        map.put("last7Days", last7Days());
        map.put("topShopsByConsume", topShops(8));
        map.put("quotaAlerts", quotaAlerts(20));
        map.put("expiringSoon", expiringSoon(14, 20));
        map.put("topServices", topServices(10));
        map.put("expiredCount", scalarLong("""
            SELECT COUNT(1) FROM t_tenant
            WHERE expire_at IS NOT NULL AND expire_at < CURRENT_TIMESTAMP
            """));
        map.put("readonlyCount", scalarLong(
            "SELECT COUNT(1) FROM t_tenant WHERE write_mode = 'readonly'"
        ));
        return map;
    }

    private List<Map<String, Object>> expiringSoon(int withinDays, int limit) {
        String now = dbDialect.nowExpr();
        String until = dbDialect.nowOffsetExpr(withinDays);
        return jdbcTemplate.queryForList("""
            SELECT id, shop_name, tenant_key, plan_code, expire_at, write_mode, status
            FROM t_tenant
            WHERE expire_at IS NOT NULL
              AND expire_at >= %s
              AND expire_at <= %s
            ORDER BY expire_at ASC
            LIMIT ?
            """.formatted(now, until), limit
        ).stream().map(row -> {
            Map<String, Object> m = new HashMap<>(row);
            if (m.get("id") != null) {
                m.put("id", String.valueOf(m.get("id")));
            }
            return m;
        }).toList();
    }

    private List<Map<String, Object>> topServices(int limit) {
        String since = dbDialect.dateOffsetExpr(-30);
        return jdbcTemplate.queryForList("""
            SELECT st.name AS service_name, COUNT(1) AS total_count, COALESCE(SUM(cr.amount),0) AS total_amount
            FROM t_consume_record cr
            LEFT JOIN t_service_type st ON st.id = cr.service_type_id AND st.tenant_id = cr.tenant_id
            WHERE COALESCE(cr.status,'normal') = 'normal'
              AND DATE(cr.created_at) >= %s
            GROUP BY st.name
            ORDER BY total_amount DESC
            LIMIT ?
            """.formatted(since), limit);
    }

    private List<Map<String, Object>> last7Days() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            String dayExpr = dbDialect.dateOffsetExpr(-i);
            Map<String, Object> row = new HashMap<>();
            String day = jdbcTemplate.queryForObject("SELECT " + dayExpr, String.class);
            row.put("day", day);
            row.put("recharge", scalarDecimal(
                "SELECT COALESCE(SUM(amount),0) FROM t_recharge_record WHERE COALESCE(status,'normal')='normal' AND DATE(created_at)=" + dayExpr
            ));
            row.put("consume", scalarDecimal(
                "SELECT COALESCE(SUM(amount),0) FROM t_consume_record WHERE COALESCE(status,'normal')='normal' AND DATE(created_at)=" + dayExpr
            ));
            list.add(row);
        }
        return list;
    }

    private List<Map<String, Object>> topShops(int limit) {
        String since = dbDialect.dateOffsetExpr(-30);
        return jdbcTemplate.queryForList("""
            SELECT t.id, t.shop_name, t.tenant_key,
                   COALESCE(SUM(c.amount),0) AS total_consume
            FROM t_tenant t
            LEFT JOIN t_consume_record c
              ON c.tenant_id = t.id AND COALESCE(c.status,'normal') = 'normal'
             AND DATE(c.created_at) >= %s
            GROUP BY t.id, t.shop_name, t.tenant_key
            ORDER BY total_consume DESC
            LIMIT ?
            """.formatted(since), limit
        ).stream().map(row -> {
            Map<String, Object> m = new HashMap<>(row);
            if (m.get("id") != null) {
                m.put("id", String.valueOf(m.get("id")));
            }
            return m;
        }).toList();
    }

    private List<Map<String, Object>> quotaAlerts(int limit) {
        // 会员用量 >= 80% 上限
        List<Map<String, Object>> tenants = jdbcTemplate.queryForList("""
            SELECT t.id, t.shop_name, t.tenant_key, t.max_customers, t.max_employees,
                   (SELECT COUNT(1) FROM t_customer c WHERE c.tenant_id = t.id) AS used_customers,
                   (SELECT COUNT(1) FROM t_employee e WHERE e.tenant_id = t.id AND e.status = 'active') AS used_employees
            FROM t_tenant t
            WHERE t.status = 'active'
            """);
        List<Map<String, Object>> alerts = new ArrayList<>();
        for (Map<String, Object> t : tenants) {
            int maxC = ((Number) t.get("max_customers")).intValue();
            int maxE = ((Number) t.get("max_employees")).intValue();
            int usedC = ((Number) t.get("used_customers")).intValue();
            int usedE = ((Number) t.get("used_employees")).intValue();
            boolean warnC = maxC > 0 && usedC * 100 >= maxC * 80;
            boolean warnE = maxE > 0 && usedE * 100 >= maxE * 80;
            if (!warnC && !warnE) {
                continue;
            }
            Map<String, Object> a = new HashMap<>();
            a.put("id", String.valueOf(t.get("id")));
            a.put("shop_name", t.get("shop_name"));
            a.put("tenant_key", t.get("tenant_key"));
            a.put("used_customers", usedC);
            a.put("max_customers", maxC);
            a.put("used_employees", usedE);
            a.put("max_employees", maxE);
            a.put("customerWarn", warnC);
            a.put("employeeWarn", warnE);
            alerts.add(a);
            if (alerts.size() >= limit) {
                break;
            }
        }
        return alerts;
    }

    private long scalarLong(String sql) {
        Long v = jdbcTemplate.queryForObject(sql, Long.class);
        return v == null ? 0L : v;
    }

    private BigDecimal scalarDecimal(String sql) {
        BigDecimal v = jdbcTemplate.queryForObject(sql, BigDecimal.class);
        return v == null ? BigDecimal.ZERO : v;
    }
}
