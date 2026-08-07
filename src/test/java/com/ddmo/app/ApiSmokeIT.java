package com.ddmo.app;

import com.ddmo.app.support.MockMvcAuthSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
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
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * C 端接口 happy-path 冒烟：注册登录后串联主业务与只读查询。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApiSmokeIT {

    private static final Path DB_PATH;

    static {
        try {
            Path dir = Path.of("target", "test-db");
            Files.createDirectories(dir);
            DB_PATH = dir.resolve("smoke-" + UUID.randomUUID() + ".db");
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
        registry.add("app.backup.dir", () -> Path.of("target", "test-backups", "smoke").toAbsolutePath().toString());
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private String token;
    private String customerId;
    private String employeeId;
    private String serviceTypeId;
    private String rechargeId;
    private String consumeId;
    private String staffId;
    private final String user = MockMvcAuthSupport.uniqueUser("smoke");
    private final String pass = "pass1234";

    @BeforeAll
    void bootstrap() throws Exception {
        MockMvcAuthSupport.registerOwner(mockMvc, user, pass, "烟店");
        token = MockMvcAuthSupport.loginToken(mockMvc, objectMapper, user, pass);
        assertThat(token).isNotBlank();
    }

    @Test
    @Order(1)
    void publicAndAuthBasics() throws Exception {
        mockMvc.perform(get("/api/install/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/auth/register-status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        // G6：产品线接口公开，无需登录
        mockMvc.perform(get("/api/system/product-line"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/system/access-info")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.username").value(user));

        mockMvc.perform(post("/api/auth/logout")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @Order(2)
    void customerEmployeeServiceCrud() throws Exception {
        MvcResult cust = mockMvc.perform(post("/api/customers")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"会员甲","phone":"13800138001","verifyCode":"8001","initialRechargeAmount":0,"remark":"冒烟"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andReturn();
        customerId = MockMvcAuthSupport.dataId(objectMapper, cust);

        mockMvc.perform(get("/api/customers")
                .header("Authorization", "Bearer " + token)
                .param("page", "1")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(put("/api/customers/" + customerId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"会员甲改","phone":"13800138001","verifyCode":"8001","remark":"改"}
                    """))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/accounts/" + customerId + "/balance")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        MvcResult emp = mockMvc.perform(post("/api/employees")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"理发师冒烟"}
                    """))
            .andExpect(status().isOk())
            .andReturn();
        employeeId = MockMvcAuthSupport.dataId(objectMapper, emp);

        mockMvc.perform(get("/api/employees")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/employees/options")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());

        mockMvc.perform(put("/api/employees/" + employeeId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"理发师改"}
                    """))
            .andExpect(status().isOk());

        MvcResult svc = mockMvc.perform(post("/api/config/services")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"剪发冒烟","price":30}
                    """))
            .andExpect(status().isOk())
            .andReturn();
        serviceTypeId = MockMvcAuthSupport.dataId(objectMapper, svc);

        mockMvc.perform(get("/api/config/services")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/config/services/options")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());

        mockMvc.perform(put("/api/config/services/" + serviceTypeId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"剪发改","price":35}
                    """))
            .andExpect(status().isOk());
    }

    @Test
    @Order(3)
    void rechargeConsumeReverse() throws Exception {
        MvcResult recharge = mockMvc.perform(post("/api/transactions/recharge")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"customerId":"%s","amount":100,"remark":"冒烟充值"}
                    """.formatted(customerId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andReturn();
        rechargeId = MockMvcAuthSupport.dataId(objectMapper, recharge);

        MvcResult consume = mockMvc.perform(post("/api/transactions/consume")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"customerId":"%s","employeeId":"%s","serviceTypeId":"%s","verifyCode":"8001","amount":20}
                    """.formatted(customerId, employeeId, serviceTypeId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andReturn();
        consumeId = MockMvcAuthSupport.dataId(objectMapper, consume);

        mockMvc.perform(get("/api/transactions")
                .header("Authorization", "Bearer " + token)
                .param("page", "1")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/transactions/consume/" + consumeId + "/reverse")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"reason":"冒烟冲正消费"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        // 再消费一笔后冲正充值会因余额不足失败；此处仅再充值再冲正该笔
        MvcResult r2 = mockMvc.perform(post("/api/transactions/recharge")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"customerId":"%s","amount":50}
                    """.formatted(customerId)))
            .andExpect(status().isOk())
            .andReturn();
        String r2Id = MockMvcAuthSupport.dataId(objectMapper, r2);

        mockMvc.perform(post("/api/transactions/recharge/" + r2Id + "/reverse")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"reason":"冒烟冲正充值"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @Order(4)
    void staffSettingsShopReports() throws Exception {
        MvcResult staff = mockMvc.perform(post("/api/staff")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"%s","password":"staff123","nickname":"收银","role":"cashier"}
                    """.formatted(MockMvcAuthSupport.uniqueUser("cashier"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andReturn();
        staffId = MockMvcAuthSupport.dataId(objectMapper, staff);

        mockMvc.perform(get("/api/staff")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/staff/roles")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());

        mockMvc.perform(put("/api/staff/" + staffId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"nickname":"收银改"}
                    """))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/staff/" + staffId + "/reset-password")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"newPassword":"staff456"}
                    """))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/settings")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
        mockMvc.perform(put("/api/settings")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"settings":{"dailyTarget":"1000"}}
                    """))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/shop")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
        mockMvc.perform(put("/api/shop")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"shopName":"冒烟门店"}
                    """))
            .andExpect(status().isOk());

        String today = LocalDate.now().toString();
        mockMvc.perform(get("/api/reports/dashboard")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/reports/summary")
                .header("Authorization", "Bearer " + token)
                .param("startDate", today)
                .param("endDate", today))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/reports/employee-performance")
                .header("Authorization", "Bearer " + token)
                .param("startDate", today)
                .param("endDate", today))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/reports/service-breakdown")
                .header("Authorization", "Bearer " + token)
                .param("startDate", today)
                .param("endDate", today))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/export/customers")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/export/transactions")
                .header("Authorization", "Bearer " + token)
                .param("startDate", today)
                .param("endDate", today))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/export/employee-performance")
                .header("Authorization", "Bearer " + token)
                .param("startDate", today)
                .param("endDate", today))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/audit/logs")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/announcements")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/system/backup")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @Order(5)
    void toggleStatusAndUnauthorized() throws Exception {
        mockMvc.perform(patch("/api/customers/" + customerId + "/toggle-status")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
        // 恢复可用
        mockMvc.perform(patch("/api/customers/" + customerId + "/toggle-status")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());

        mockMvc.perform(patch("/api/employees/" + employeeId + "/toggle-status")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
        mockMvc.perform(patch("/api/employees/" + employeeId + "/toggle-status")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());

        mockMvc.perform(patch("/api/config/services/" + serviceTypeId + "/toggle-status")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
        mockMvc.perform(patch("/api/config/services/" + serviceTypeId + "/toggle-status")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());

        mockMvc.perform(patch("/api/staff/" + staffId + "/toggle-status")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/customers"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(6)
    void desktopBlocksWxAndSaas() throws Exception {
        mockMvc.perform(post("/api/auth/wx-login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"code":"test-code"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(get("/api/saas/dashboard")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden());
    }
}
