package com.ddmo.saas.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SaasPlanService {

    private final JdbcTemplate jdbcTemplate;

    public SaasPlanService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> listActive() {
        return jdbcTemplate.queryForList("""
            SELECT code, name, max_customers, max_employees, trial_days, description, sort_order, status
            FROM t_saas_plan
            WHERE status = 'active'
            ORDER BY sort_order ASC
            """
        ).stream().map(HashMap::new).collect(Collectors.toList());
    }

    public Map<String, Object> getByCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("套餐代码不能为空");
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT code, name, max_customers, max_employees, trial_days, description FROM t_saas_plan WHERE code = ?",
            code.trim()
        );
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("套餐不存在: " + code);
        }
        return new HashMap<>(rows.get(0));
    }
}
