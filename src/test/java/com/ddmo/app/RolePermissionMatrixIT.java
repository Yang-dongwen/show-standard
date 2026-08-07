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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * G9：收银员 / 店员权限矩阵抽查（403）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RolePermissionMatrixIT {

    private static final Path DB_PATH;

    static {
        try {
            Path dir = Path.of("target", "test-db");
            Files.createDirectories(dir);
            DB_PATH = dir.resolve("role-matrix-" + UUID.randomUUID() + ".db");
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

    private String ownerToken;
    private String cashierToken;
    private String staffToken;
    private final String cashierUser = MockMvcAuthSupport.uniqueUser("cash");
    private final String staffUser = MockMvcAuthSupport.uniqueUser("stf");

    @BeforeAll
    void setupRoles() throws Exception {
        String owner = MockMvcAuthSupport.uniqueUser("own");
        MockMvcAuthSupport.registerOwner(mockMvc, owner, "pass1234", "权店");
        ownerToken = MockMvcAuthSupport.loginToken(mockMvc, objectMapper, owner, "pass1234");

        mockMvc.perform(post("/api/staff")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"%s","password":"pass1234","nickname":"收银","role":"cashier"}
                    """.formatted(cashierUser)))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/staff")
                .header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"%s","password":"pass1234","nickname":"店员","role":"staff"}
                    """.formatted(staffUser)))
            .andExpect(status().isOk());

        cashierToken = MockMvcAuthSupport.loginToken(mockMvc, objectMapper, cashierUser, "pass1234");
        staffToken = MockMvcAuthSupport.loginToken(mockMvc, objectMapper, staffUser, "pass1234");
    }

    @Test
    void cashierCanRechargeButNotManageStaffOrBackup() throws Exception {
        mockMvc.perform(get("/api/customers")
                .header("Authorization", "Bearer " + cashierToken))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/staff")
                .header("Authorization", "Bearer " + cashierToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(get("/api/system/backup")
                .header("Authorization", "Bearer " + cashierToken))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/audit/logs")
                .header("Authorization", "Bearer " + cashierToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void staffCanConsumeButNotRechargeOrReverseOrSettings() throws Exception {
        // 店员可读会员列表
        mockMvc.perform(get("/api/customers")
                .header("Authorization", "Bearer " + staffToken))
            .andExpect(status().isOk());

        // 不可充值
        mockMvc.perform(post("/api/transactions/recharge")
                .header("Authorization", "Bearer " + staffToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"customerId":"1","amount":10}
                    """))
            .andExpect(status().isForbidden());

        // 不可冲正
        mockMvc.perform(post("/api/transactions/recharge/1/reverse")
                .header("Authorization", "Bearer " + staffToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isForbidden());

        // 不可改设置
        mockMvc.perform(get("/api/settings")
                .header("Authorization", "Bearer " + staffToken))
            .andExpect(status().isForbidden());

        // 不可写服务
        mockMvc.perform(post("/api/config/services")
                .header("Authorization", "Bearer " + staffToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"x","price":1}
                    """))
            .andExpect(status().isForbidden());
    }

    @Test
    void ownerKeepsFullAccess() throws Exception {
        mockMvc.perform(get("/api/staff")
                .header("Authorization", "Bearer " + ownerToken))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/system/backup")
                .header("Authorization", "Bearer " + ownerToken))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/audit/logs")
                .header("Authorization", "Bearer " + ownerToken))
            .andExpect(status().isOk());
    }
}
