package com.ddmo.app;

import com.ddmo.app.support.MockMvcAuthSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * G8：跨租户越权——租户 A 的资源 ID 不能被租户 B 读写。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TenantIsolationIT {

    private static final Path DB_PATH;

    static {
        try {
            Path dir = Path.of("target", "test-db");
            Files.createDirectories(dir);
            DB_PATH = dir.resolve("tenant-iso-" + UUID.randomUUID() + ".db");
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

    private String tokenA;
    private String tokenB;
    private String customerA;
    private String employeeA;
    private String serviceA;

    @BeforeAll
    void twoTenants() throws Exception {
        String userA = MockMvcAuthSupport.uniqueUser("ta");
        String userB = MockMvcAuthSupport.uniqueUser("tb");
        MockMvcAuthSupport.registerOwner(mockMvc, userA, "pass1234", "甲店");
        MockMvcAuthSupport.registerOwner(mockMvc, userB, "pass1234", "乙店");
        tokenA = MockMvcAuthSupport.loginToken(mockMvc, objectMapper, userA, "pass1234");
        tokenB = MockMvcAuthSupport.loginToken(mockMvc, objectMapper, userB, "pass1234");

        MvcResult cust = mockMvc.perform(post("/api/customers")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"甲会员","phone":"13600136001","verifyCode":"6001"}
                    """))
            .andExpect(status().isOk())
            .andReturn();
        customerA = MockMvcAuthSupport.dataId(objectMapper, cust);

        MvcResult emp = mockMvc.perform(post("/api/employees")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"甲员工"}
                    """))
            .andExpect(status().isOk())
            .andReturn();
        employeeA = MockMvcAuthSupport.dataId(objectMapper, emp);

        MvcResult svc = mockMvc.perform(post("/api/config/services")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"甲服务","price":10}
                    """))
            .andExpect(status().isOk())
            .andReturn();
        serviceA = MockMvcAuthSupport.dataId(objectMapper, svc);

        mockMvc.perform(post("/api/transactions/recharge")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"customerId":"%s","amount":50}
                    """.formatted(customerA)))
            .andExpect(status().isOk());
    }

    @Test
    void tenantBCannotUpdateTenantACustomer() throws Exception {
        mockMvc.perform(put("/api/customers/" + customerA)
                .header("Authorization", "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"窃取","phone":"13600136099"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void tenantBCannotReadTenantABalanceAsOwn() throws Exception {
        // 余额接口按当前租户过滤：跨租户 ID 应失败（会员不存在）
        mockMvc.perform(get("/api/accounts/" + customerA + "/balance")
                .header("Authorization", "Bearer " + tokenB))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void tenantBCannotConsumeOnTenantACustomer() throws Exception {
        mockMvc.perform(post("/api/transactions/consume")
                .header("Authorization", "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"customerId":"%s","employeeId":"%s","serviceTypeId":"%s","verifyCode":"6001","amount":1}
                    """.formatted(customerA, employeeA, serviceA)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void tenantBCannotUpdateTenantAEmployee() throws Exception {
        mockMvc.perform(put("/api/employees/" + employeeA)
                .header("Authorization", "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"窜改"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));
    }
}
