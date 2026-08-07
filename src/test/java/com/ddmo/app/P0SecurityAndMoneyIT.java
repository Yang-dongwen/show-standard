package com.ddmo.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P0：资金路径 + 租户门禁（停用/只读）最小集成测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class P0SecurityAndMoneyIT {

    private static final Path DB_PATH;

    static {
        try {
            Path dir = Path.of("target", "test-db");
            Files.createDirectories(dir);
            DB_PATH = dir.resolve("show-it-" + UUID.randomUUID() + ".db");
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DB_PATH.toAbsolutePath());
        registry.add("app.deployment", () -> "desktop");
        registry.add("app.register.mode", () -> "open");
        registry.add("app.saas.bootstrap-enabled", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static String token;
    private static long tenantId;
    private static String customerId;
    private static String employeeId;
    private static String serviceTypeId;
    private static final String USER = "it_owner_" + UUID.randomUUID().toString().substring(0, 8);
    private static final String PASS = "pass1234";

    @BeforeEach
    void resetTenantFlags() {
        if (tenantId > 0) {
            jdbcTemplate.update(
                "UPDATE t_tenant SET status = 'active', write_mode = 'normal', expire_at = NULL WHERE id = ?",
                tenantId
            );
        }
    }

    @Test
    @Order(1)
    void registerAndLogin() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"%s","password":"%s","nickname":"测店"}
                    """.formatted(USER, PASS)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"%s","password":"%s"}
                    """.formatted(USER, PASS)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.token").isString())
            .andReturn();

        JsonNode body = objectMapper.readTree(login.getResponse().getContentAsString());
        token = body.path("data").path("token").asText();
        assertThat(token).isNotBlank();

        tenantId = jdbcTemplate.queryForObject(
            "SELECT tenant_id FROM t_manager WHERE username = ?", Long.class, USER
        );
        assertThat(tenantId).isPositive();
    }

    @Test
    @Order(2)
    void rechargeAndConsume() throws Exception {
        assertThat(token).isNotBlank();

        MvcResult cust = mockMvc.perform(post("/api/customers")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"会员甲","phone":"13800138000","verifyCode":"8000","initialRechargeAmount":0}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andReturn();
        customerId = objectMapper.readTree(cust.getResponse().getContentAsString())
            .path("data").path("id").asText();

        MvcResult emp = mockMvc.perform(post("/api/employees")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"理发师A"}
                    """))
            .andExpect(status().isOk())
            .andReturn();
        employeeId = objectMapper.readTree(emp.getResponse().getContentAsString())
            .path("data").path("id").asText();

        MvcResult services = mockMvc.perform(get("/api/config/services")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();
        JsonNode svcList = objectMapper.readTree(services.getResponse().getContentAsString()).path("data");
        if (svcList.isArray() && !svcList.isEmpty()) {
            serviceTypeId = svcList.get(0).path("id").asText();
        } else if (svcList.has("list") && svcList.path("list").isArray() && !svcList.path("list").isEmpty()) {
            serviceTypeId = svcList.path("list").get(0).path("id").asText();
        } else {
            // 兜底：直接查库
            serviceTypeId = String.valueOf(jdbcTemplate.queryForObject(
                "SELECT id FROM t_service_type WHERE tenant_id = ? LIMIT 1", Long.class, tenantId
            ));
        }

        mockMvc.perform(post("/api/transactions/recharge")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"customerId":"%s","amount":100,"remark":"测试充值"}
                    """.formatted(customerId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/transactions/consume")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"customerId":"%s","employeeId":"%s","serviceTypeId":"%s","verifyCode":"8000","amount":30}
                    """.formatted(customerId, employeeId, serviceTypeId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        java.math.BigDecimal balance = jdbcTemplate.queryForObject(
            "SELECT balance FROM t_account WHERE tenant_id = ? AND customer_id = ?",
            java.math.BigDecimal.class, tenantId, Long.parseLong(customerId)
        );
        assertThat(balance).isEqualByComparingTo("70");
    }

    @Test
    @Order(3)
    void suspendedTenantRejectedOnApi() throws Exception {
        assertThat(token).isNotBlank();
        jdbcTemplate.update("UPDATE t_tenant SET status = 'suspended' WHERE id = ?", tenantId);

        mockMvc.perform(get("/api/customers")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @Order(4)
    void readonlyBlocksWrite() throws Exception {
        assertThat(token).isNotBlank();
        jdbcTemplate.update(
            "UPDATE t_tenant SET status = 'active', write_mode = 'readonly' WHERE id = ?",
            tenantId
        );

        // 读允许
        mockMvc.perform(get("/api/customers")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        // 写拒绝
        mockMvc.perform(post("/api/customers")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"乙","phone":"13900139000","verifyCode":"9000"}
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false));
    }
}
