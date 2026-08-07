package com.ddmo.saas.service;

import com.ddmo.app.config.AppDeploymentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 幂等写入 SaaS 套餐目录。仅 cloud / SaaS 云版执行。
 */
@Component
@Order(150)
public class SaasPlanBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SaasPlanBootstrap.class);

    private final JdbcTemplate jdbcTemplate;
    private final AppDeploymentProperties deploymentProperties;

    public SaasPlanBootstrap(JdbcTemplate jdbcTemplate, AppDeploymentProperties deploymentProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.deploymentProperties = deploymentProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!deploymentProperties.isSaasEnabled()) {
            return;
        }
        upsertPlan("free", "免费版", 500, 5, 0, "适合试用与小店", 10);
        upsertPlan("plus", "Plus", 2000, 20, 14, "成长门店", 20);
        upsertPlan("pro", "Pro", 10000, 100, 30, "连锁/大体量", 30);
    }

    private void upsertPlan(String code, String name, int maxC, int maxE, int trial, String desc, int sort) {
        try {
            Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM t_saas_plan WHERE code = ?", Integer.class, code
            );
            if (n != null && n > 0) {
                return;
            }
            jdbcTemplate.update("""
                    INSERT INTO t_saas_plan(code, name, max_customers, max_employees, trial_days, description, sort_order, status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 'active')
                    """,
                code, name, maxC, maxE, trial, desc, sort
            );
            log.info("已种子套餐: {}", code);
        } catch (Exception e) {
            log.warn("种子套餐 {} 失败（表可能尚未就绪）: {}", code, e.getMessage());
        }
    }
}
