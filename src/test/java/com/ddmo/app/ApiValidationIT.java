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
import org.springframework.test.web.servlet.ResultActions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * C 端写接口负例：缺字段、边界值、业务约束；记录 400 vs 500 行为。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApiValidationIT {

    private static final Path DB_PATH;

    static {
        try {
            Path dir = Path.of("target", "test-db");
            Files.createDirectories(dir);
            DB_PATH = dir.resolve("validation-" + UUID.randomUUID() + ".db");
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
        registry.add("app.backup.dir", () -> Path.of("target", "test-backups", "validation").toAbsolutePath().toString());
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private String token;
    private String customerId;
    private String employeeId;
    private String serviceTypeId;
    private final String user = MockMvcAuthSupport.uniqueUser("val");
    private final String pass = "pass1234";

    @BeforeAll
    void bootstrap() throws Exception {
        MockMvcAuthSupport.registerOwner(mockMvc, user, pass, "验店");
        token = MockMvcAuthSupport.loginToken(mockMvc, objectMapper, user, pass);

        MvcResult cust = mockMvc.perform(post("/api/customers")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"验会员","phone":"13900139001","verifyCode":"9001"}
                    """))
            .andExpect(status().isOk())
            .andReturn();
        customerId = MockMvcAuthSupport.dataId(objectMapper, cust);

        MvcResult emp = mockMvc.perform(post("/api/employees")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"验员工"}
                    """))
            .andExpect(status().isOk())
            .andReturn();
        employeeId = MockMvcAuthSupport.dataId(objectMapper, emp);

        MvcResult svc = mockMvc.perform(post("/api/config/services")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"验服务","price":10}
                    """))
            .andExpect(status().isOk())
            .andReturn();
        serviceTypeId = MockMvcAuthSupport.dataId(objectMapper, svc);

        mockMvc.perform(post("/api/transactions/recharge")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"customerId":"%s","amount":100}
                    """.formatted(customerId)))
            .andExpect(status().isOk());
    }

    @Test
    @Order(1)
    void authValidation() throws Exception {
        expectBadRequest(mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"username":"","password":"123456","nickname":"x"}
                """)));

        expectBadRequest(mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"username":"u1","password":"123","nickname":"x"}
                """)));

        expectBadRequest(mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"username":"u2","password":"123456","nickname":"过长昵称七八九"}
                """)));

        expectBadRequest(mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"username":"%s","password":"wrong-pass"}
                """.formatted(user))));

        expectBadRequest(mockMvc.perform(post("/api/auth/change-password")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"oldPassword":"%s","newPassword":"12"}
                """.formatted(pass))));
    }

    @Test
    @Order(2)
    void customerValidation() throws Exception {
        expectBadRequest(mockMvc.perform(post("/api/customers")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"name":"","phone":"13900139002"}
                """)));

        expectBadRequest(mockMvc.perform(post("/api/customers")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"name":"无手机","phone":""}
                """)));

        // 重复手机
        expectBadRequest(mockMvc.perform(post("/api/customers")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"name":"重复","phone":"13900139001"}
                """)));

        // 非法校验码
        expectBadRequest(mockMvc.perform(post("/api/customers")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"name":"码错","phone":"13900139003","verifyCode":"12"}
                """)));

        // 手机号格式
        expectBadRequest(mockMvc.perform(post("/api/customers")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"name":"格式","phone":"12345"}
                """)));

        // 非法 id
        expectBadRequest(mockMvc.perform(put("/api/customers/not-a-number")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"name":"x","phone":"13900139004"}
                """)));
    }

    @Test
    @Order(3)
    void transactionValidation() throws Exception {
        expectBadRequest(mockMvc.perform(post("/api/transactions/recharge")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"customerId":"%s","amount":0}
                """.formatted(customerId))));

        expectBadRequest(mockMvc.perform(post("/api/transactions/recharge")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"customerId":"%s","amount":-1}
                """.formatted(customerId))));

        expectBadRequest(mockMvc.perform(post("/api/transactions/consume")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"customerId":"%s","employeeId":"%s","serviceTypeId":"%s","verifyCode":"0000","amount":10}
                """.formatted(customerId, employeeId, serviceTypeId))));

        expectBadRequest(mockMvc.perform(post("/api/transactions/consume")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"customerId":"%s","employeeId":"%s","serviceTypeId":"%s","verifyCode":"9001","amount":999999}
                """.formatted(customerId, employeeId, serviceTypeId))));

        expectBadRequest(mockMvc.perform(post("/api/transactions/recharge/" + UUID.randomUUID() + "/reverse")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}")));
    }

    @Test
    @Order(4)
    void employeeServiceStaffValidation() throws Exception {
        expectBadRequest(mockMvc.perform(post("/api/employees")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"name":""}
                """)));

        expectBadRequest(mockMvc.perform(post("/api/config/services")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"name":"","price":1}
                """)));

        expectBadRequest(mockMvc.perform(post("/api/config/services")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"name":"负价","price":-1}
                """)));

        expectBadRequest(mockMvc.perform(post("/api/staff")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"username":"s1","password":"12","nickname":"n","role":"cashier"}
                """)));

        expectBadRequest(mockMvc.perform(post("/api/staff")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"username":"s2","password":"123456","nickname":"n","role":"owner"}
                """)));

        expectBadRequest(mockMvc.perform(put("/api/shop")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"shopName":""}
                """)));

        expectBadRequest(mockMvc.perform(put("/api/settings")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"settings":{}}
                """)));

        expectBadRequest(mockMvc.perform(put("/api/settings")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"settings":{"dailyTarget":"-1"}}
                """)));

        // G4：非白名单 key
        expectBadRequest(mockMvc.perform(put("/api/settings")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"settings":{"evil_key":"1"}}
                """)));

        // G2：姓名过长
        expectBadRequest(mockMvc.perform(post("/api/customers")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"name":"%s","phone":"13700137001"}
                """.formatted("名".repeat(33)))));

        // G3：金额过大
        expectBadRequest(mockMvc.perform(post("/api/transactions/recharge")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"customerId":"%s","amount":100000000}
                """.formatted(customerId))));
    }

    @Test
    @Order(5)
    void backupPathTraversalAndNullBody() throws Exception {
        expectBadRequest(mockMvc.perform(get("/api/system/backup/..evil.db")
            .header("Authorization", "Bearer " + token)));

        // 空 JSON 对象：缺必填字段 → 业务 400（非 NPE 500）
        expectBadRequest(mockMvc.perform(post("/api/customers")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}")));

        // 完全无 body：HttpMessageNotReadable → 400
        expectBadRequest(mockMvc.perform(post("/api/customers")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)));
    }

    private static void expectBadRequest(ResultActions actions) throws Exception {
        actions.andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));
    }
}
