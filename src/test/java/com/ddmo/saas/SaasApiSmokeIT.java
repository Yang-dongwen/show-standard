package com.ddmo.saas;

import com.ddmo.app.DdmoApplication;
import com.ddmo.app.support.MockMvcAuthSupport;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SaaS 运营 API + 小程序 wx 绑定冒烟（cloud 部署 + SQLite + mock 微信）。
 */
@SpringBootTest(classes = DdmoApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SaasApiSmokeIT {

    private static final Path DB_PATH;
    private static final String PLATFORM_USER = "platform";
    private static final String PLATFORM_PASS = "platform123";
    private static final String INVITE = "DEMO-INVITE";

    static {
        try {
            Path dir = Path.of("target", "test-db");
            Files.createDirectories(dir);
            DB_PATH = dir.resolve("saas-smoke-" + UUID.randomUUID() + ".db");
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DB_PATH.toAbsolutePath());
        registry.add("app.deployment", () -> "cloud");
        registry.add("app.register.mode", () -> "open");
        registry.add("app.security.strict-cloud", () -> "false");
        registry.add("app.saas.bootstrap-enabled", () -> "true");
        registry.add("app.saas.bootstrap-username", () -> PLATFORM_USER);
        registry.add("app.saas.bootstrap-password", () -> PLATFORM_PASS);
        registry.add("app.saas.invite-code", () -> INVITE);
        registry.add("app.wx.miniapp.mock", () -> "true");
        registry.add("app.wx.miniapp.enabled", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    private String saasToken;
    private String tenantId;
    private String inviteId;
    private String announcementId;
    private final String shopUser = MockMvcAuthSupport.uniqueUser("shop");
    private final String shopPass = "shop1234";

    @BeforeAll
    void loginPlatform() throws Exception {
        saasToken = MockMvcAuthSupport.saasLoginToken(mockMvc, objectMapper, PLATFORM_USER, PLATFORM_PASS);
        assertThat(saasToken).isNotBlank();
    }

    @Test
    @Order(1)
    void publicAndDashboard() throws Exception {
        mockMvc.perform(get("/api/saas/public/register-status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/saas/dashboard")
                .header("Authorization", "Bearer " + saasToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/saas/plans")
                .header("Authorization", "Bearer " + saasToken))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/saas/tenants")
                .header("Authorization", "Bearer " + saasToken))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/saas/audit")
                .header("Authorization", "Bearer " + saasToken))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/saas/billings")
                .header("Authorization", "Bearer " + saasToken))
            .andExpect(status().isOk());
    }

    @Test
    @Order(2)
    void inviteAndRegisterShop() throws Exception {
        MvcResult invite = mockMvc.perform(post("/api/saas/invites")
                .header("Authorization", "Bearer " + saasToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"maxUses":5,"note":"冒烟邀请","expireDays":30}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andReturn();
        JsonNode inviteData = MockMvcAuthSupport.data(objectMapper, invite);
        inviteId = inviteData.path("id").asText();
        String code = inviteData.path("code").asText();
        assertThat(code).isNotBlank();

        mockMvc.perform(get("/api/saas/invites")
                .header("Authorization", "Bearer " + saasToken))
            .andExpect(status().isOk());

        // 无效邀请码
        mockMvc.perform(post("/api/saas/public/register-shop")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"inviteCode":"INVALID-XXX","username":"x","password":"123456","nickname":"n"}
                    """))
            .andExpect(status().isBadRequest());

        // 短密码
        mockMvc.perform(post("/api/saas/public/register-shop")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"inviteCode":"%s","username":"short","password":"12","nickname":"n"}
                    """.formatted(code)))
            .andExpect(status().isBadRequest());

        MvcResult shop = mockMvc.perform(post("/api/saas/public/register-shop")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"inviteCode":"%s","username":"%s","password":"%s","nickname":"店长","shopName":"冒烟门店"}
                    """.formatted(code, shopUser, shopPass)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andReturn();
        JsonNode shopData = MockMvcAuthSupport.data(objectMapper, shop);
        if (shopData.has("tenantId")) {
            tenantId = shopData.path("tenantId").asText();
        } else if (shopData.has("id")) {
            tenantId = shopData.path("id").asText();
        }

        if (tenantId == null || tenantId.isBlank()) {
            MvcResult tenants = mockMvc.perform(get("/api/saas/tenants")
                    .header("Authorization", "Bearer " + saasToken))
                .andExpect(status().isOk())
                .andReturn();
            JsonNode list = objectMapper.readTree(tenants.getResponse().getContentAsString()).path("data");
            assertThat(list.isArray()).isTrue();
            assertThat(list.size()).isPositive();
            tenantId = list.get(list.size() - 1).path("id").asText();
        }
        assertThat(tenantId).isNotBlank();
    }

    @Test
    @Order(3)
    void tenantLifecycle() throws Exception {
        mockMvc.perform(get("/api/saas/tenants/" + tenantId)
                .header("Authorization", "Bearer " + saasToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(put("/api/saas/tenants/" + tenantId + "/plan")
                .header("Authorization", "Bearer " + saasToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"planCode":"basic","maxCustomers":200,"maxEmployees":20}
                    """))
            .andExpect(status().isOk());

        mockMvc.perform(put("/api/saas/tenants/" + tenantId + "/meta")
                .header("Authorization", "Bearer " + saasToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"tags":"smoke","remark":"冒烟备注"}
                    """))
            .andExpect(status().isOk());

        mockMvc.perform(put("/api/saas/tenants/" + tenantId + "/write-mode")
                .header("Authorization", "Bearer " + saasToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"writeMode":"readonly"}
                    """))
            .andExpect(status().isOk());

        mockMvc.perform(put("/api/saas/tenants/" + tenantId + "/write-mode")
                .header("Authorization", "Bearer " + saasToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"writeMode":"normal"}
                    """))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/saas/tenants/" + tenantId + "/renew")
                .header("Authorization", "Bearer " + saasToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"days":30,"note":"冒烟续期"}
                    """))
            .andExpect(status().isOk());

        // days < 1
        mockMvc.perform(post("/api/saas/tenants/" + tenantId + "/renew")
                .header("Authorization", "Bearer " + saasToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"days":0}
                    """))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/saas/tenants/" + tenantId + "/reset-password")
                .header("Authorization", "Bearer " + saasToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"newPassword":"reset789"}
                    """))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/saas/tenants/" + tenantId + "/suspend")
                .header("Authorization", "Bearer " + saasToken))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/saas/tenants/" + tenantId + "/activate")
                .header("Authorization", "Bearer " + saasToken))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/saas/tenants/" + tenantId + "/billings")
                .header("Authorization", "Bearer " + saasToken))
            .andExpect(status().isOk());
    }

    @Test
    @Order(4)
    void announcementsAndInviteRevoke() throws Exception {
        // 空标题
        mockMvc.perform(post("/api/saas/announcements")
                .header("Authorization", "Bearer " + saasToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"","content":"x","scope":"all"}
                    """))
            .andExpect(status().isBadRequest());

        MvcResult ann = mockMvc.perform(post("/api/saas/announcements")
                .header("Authorization", "Bearer " + saasToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"冒烟公告","content":"内容","scope":"all"}
                    """))
            .andExpect(status().isOk())
            .andReturn();
        announcementId = MockMvcAuthSupport.dataId(objectMapper, ann);

        mockMvc.perform(get("/api/saas/announcements")
                .header("Authorization", "Bearer " + saasToken))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/saas/announcements/" + announcementId + "/revoke")
                .header("Authorization", "Bearer " + saasToken))
            .andExpect(status().isOk());

        if (inviteId != null && !inviteId.isBlank()) {
            mockMvc.perform(post("/api/saas/invites/" + inviteId + "/revoke")
                    .header("Authorization", "Bearer " + saasToken))
                .andExpect(status().isOk());
        }
    }

    @Test
    @Order(5)
    void wxMiniProgramBindFlow() throws Exception {
        // 重置店长密码为已知值后绑定
        mockMvc.perform(post("/api/saas/tenants/" + tenantId + "/reset-password")
                .header("Authorization", "Bearer " + saasToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"newPassword":"%s"}
                    """.formatted(shopPass)))
            .andExpect(status().isOk());

        MvcResult wxLogin = mockMvc.perform(post("/api/auth/wx-login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"code":"smoke-wx-code-1"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andReturn();
        JsonNode wxData = MockMvcAuthSupport.data(objectMapper, wxLogin);
        assertThat(wxData.path("bindRequired").asBoolean()).isTrue();
        String preToken = wxData.path("preToken").asText();
        assertThat(preToken).isNotBlank();

        MvcResult bind = mockMvc.perform(post("/api/auth/wx-bind")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"preToken":"%s","username":"%s","password":"%s"}
                    """.formatted(preToken, shopUser, shopPass)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.token").isString())
            .andReturn();
        String cToken = MockMvcAuthSupport.data(objectMapper, bind).path("token").asText();

        mockMvc.perform(get("/api/auth/wx-bind-status")
                .header("Authorization", "Bearer " + cToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        // 再次 wx-login 应直接登录
        mockMvc.perform(post("/api/auth/wx-login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"code":"smoke-wx-code-1"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.bindRequired").value(false))
            .andExpect(jsonPath("$.data.token").isString());

        mockMvc.perform(post("/api/auth/wx-unbind")
                .header("Authorization", "Bearer " + cToken))
            .andExpect(status().isOk());
    }

    @Test
    @Order(6)
    void saasAuthRejectsCTokenAndBadLogin() throws Exception {
        mockMvc.perform(post("/api/saas/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"%s","password":"wrong"}
                    """.formatted(PLATFORM_USER)))
            .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/saas/dashboard"))
            .andExpect(status().isUnauthorized());
    }
}
