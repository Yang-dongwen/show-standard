package com.ddmo.saas.controller;

import com.ddmo.app.dto.ApiResponse;
import com.ddmo.app.dto.LoginRequest;
import com.ddmo.saas.dto.AnnouncementRequest;
import com.ddmo.saas.dto.CreateInviteRequest;
import com.ddmo.saas.dto.RegisterShopRequest;
import com.ddmo.saas.dto.RenewTenantRequest;
import com.ddmo.saas.dto.ResetPasswordRequest;
import com.ddmo.saas.dto.UpdateTenantMetaRequest;
import com.ddmo.saas.dto.UpdateTenantPlanRequest;
import com.ddmo.saas.dto.WriteModeRequest;
import com.ddmo.saas.service.SaasAnnouncementService;
import com.ddmo.saas.service.SaasAuditService;
import com.ddmo.saas.service.SaasAuthService;
import com.ddmo.saas.service.SaasBillingService;
import com.ddmo.saas.service.SaasDashboardService;
import com.ddmo.saas.service.SaasInviteService;
import com.ddmo.saas.service.SaasPlanService;
import com.ddmo.saas.service.SaasTenantService;
import com.ddmo.app.security.LoginRateLimiter;
import com.ddmo.app.util.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * SaaS 运营 API（与 C 端 /api/** 隔离）。
 */
@RestController
@RequestMapping("/api/saas")
public class SaasController {

    private final SaasAuthService authService;
    private final SaasTenantService tenantService;
    private final SaasInviteService inviteService;
    private final SaasDashboardService dashboardService;
    private final SaasAuditService auditService;
    private final SaasAnnouncementService announcementService;
    private final SaasPlanService planService;
    private final SaasBillingService billingService;
    private final LoginRateLimiter loginRateLimiter;

    public SaasController(
        SaasAuthService authService,
        SaasTenantService tenantService,
        SaasInviteService inviteService,
        SaasDashboardService dashboardService,
        SaasAuditService auditService,
        SaasAnnouncementService announcementService,
        SaasPlanService planService,
        SaasBillingService billingService,
        LoginRateLimiter loginRateLimiter
    ) {
        this.authService = authService;
        this.tenantService = tenantService;
        this.inviteService = inviteService;
        this.dashboardService = dashboardService;
        this.auditService = auditService;
        this.announcementService = announcementService;
        this.planService = planService;
        this.billingService = billingService;
        this.loginRateLimiter = loginRateLimiter;
    }

    @PostMapping("/auth/login")
    public ApiResponse<Map<String, Object>> login(
        @Valid @RequestBody LoginRequest request,
        HttpServletRequest httpRequest
    ) {
        String ip = ClientIp.from(httpRequest);
        String user = request.getUsername() != null ? request.getUsername().trim() : "";
        loginRateLimiter.assertAllowed("saas-ip:" + ip);
        loginRateLimiter.assertAllowed("saas-user:" + user);
        try {
            Map<String, Object> data = authService.login(request);
            loginRateLimiter.clear("saas-ip:" + ip);
            loginRateLimiter.clear("saas-user:" + user);
            return ApiResponse.ok("登录成功", data);
        } catch (IllegalArgumentException ex) {
            loginRateLimiter.recordFailure("saas-ip:" + ip);
            loginRateLimiter.recordFailure("saas-user:" + user);
            throw ex;
        }
    }

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard() {
        return ApiResponse.ok(dashboardService.overview());
    }

    @GetMapping("/public/register-status")
    public ApiResponse<Map<String, Object>> registerStatus() {
        return ApiResponse.ok(tenantService.publicRegisterStatus());
    }

    @PostMapping("/public/register-shop")
    public ApiResponse<Map<String, Object>> registerShop(@Valid @RequestBody RegisterShopRequest request) {
        return ApiResponse.ok("开店成功", tenantService.registerShop(request));
    }

    @GetMapping("/tenants")
    public ApiResponse<List<Map<String, Object>>> listTenants() {
        return ApiResponse.ok(tenantService.listTenants());
    }

    @GetMapping("/tenants/{id}")
    public ApiResponse<Map<String, Object>> tenantDetail(@PathVariable("id") String id) {
        return ApiResponse.ok(tenantService.tenantDetail(Long.parseLong(id)));
    }

    @PostMapping("/tenants/{id}/suspend")
    public ApiResponse<Void> suspend(@PathVariable("id") String id) {
        tenantService.setTenantStatus(Long.parseLong(id), "suspended");
        return ApiResponse.ok("已停用", null);
    }

    @PostMapping("/tenants/{id}/activate")
    public ApiResponse<Void> activate(@PathVariable("id") String id) {
        tenantService.setTenantStatus(Long.parseLong(id), "active");
        return ApiResponse.ok("已启用", null);
    }

    @PutMapping("/tenants/{id}/plan")
    public ApiResponse<Map<String, Object>> updatePlan(
        @PathVariable("id") String id,
        @RequestBody UpdateTenantPlanRequest request
    ) {
        if (request == null) {
            request = new UpdateTenantPlanRequest();
        }
        return ApiResponse.ok("套餐已更新", tenantService.updatePlan(
            Long.parseLong(id),
            request.getPlanCode(),
            request.getMaxCustomers(),
            request.getMaxEmployees()
        ));
    }

    @PostMapping("/tenants/{id}/apply-plan/{planCode}")
    public ApiResponse<Map<String, Object>> applyPlan(
        @PathVariable("id") String id,
        @PathVariable("planCode") String planCode
    ) {
        return ApiResponse.ok("已套用套餐", tenantService.applyCatalogPlan(Long.parseLong(id), planCode));
    }

    @PutMapping("/tenants/{id}/meta")
    public ApiResponse<Map<String, Object>> updateMeta(
        @PathVariable("id") String id,
        @RequestBody UpdateTenantMetaRequest request
    ) {
        if (request == null) {
            request = new UpdateTenantMetaRequest();
        }
        return ApiResponse.ok("已更新", tenantService.updateMeta(
            Long.parseLong(id),
            request.getTags(),
            request.getRemark(),
            request.getExpireDays()
        ));
    }

    @PostMapping("/tenants/{id}/reset-password")
    public ApiResponse<Void> resetPassword(
        @PathVariable("id") String id,
        @RequestBody ResetPasswordRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException("请提供新密码");
        }
        tenantService.resetManagerPassword(Long.parseLong(id), request.getNewPassword());
        return ApiResponse.ok("店长密码已重置", null);
    }

    @GetMapping("/invites")
    public ApiResponse<List<Map<String, Object>>> listInvites() {
        return ApiResponse.ok(inviteService.listAll());
    }

    @PostMapping("/invites")
    public ApiResponse<Map<String, Object>> createInvite(@RequestBody(required = false) CreateInviteRequest request) {
        if (request == null) {
            request = new CreateInviteRequest();
        }
        Map<String, Object> data = inviteService.create(
            request.getMaxUses(), request.getNote(), request.getExpireDays()
        );
        auditService.log("INVITE_CREATE", "invite", String.valueOf(data.get("id")),
            "code=" + data.get("code"));
        return ApiResponse.ok("已生成邀请码", data);
    }

    @PostMapping("/invites/{id}/revoke")
    public ApiResponse<Void> revokeInvite(@PathVariable("id") String id) {
        inviteService.revoke(Long.parseLong(id));
        auditService.log("INVITE_REVOKE", "invite", id, "吊销");
        return ApiResponse.ok("已吊销", null);
    }

    @GetMapping("/audit")
    public ApiResponse<List<Map<String, Object>>> audit(@RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(auditService.list(limit));
    }

    @GetMapping("/plans")
    public ApiResponse<List<Map<String, Object>>> plans() {
        return ApiResponse.ok(planService.listActive());
    }

    @GetMapping("/announcements")
    public ApiResponse<List<Map<String, Object>>> announcements() {
        return ApiResponse.ok(announcementService.listAll());
    }

    @PostMapping("/announcements")
    public ApiResponse<Map<String, Object>> createAnnouncement(@RequestBody AnnouncementRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        Long tid = null;
        if (request.getTenantId() != null && !request.getTenantId().isBlank()) {
            tid = Long.parseLong(request.getTenantId().trim());
        }
        return ApiResponse.ok("已发布", announcementService.create(
            request.getTitle(), request.getContent(), request.getScope(), tid
        ));
    }

    @PostMapping("/announcements/{id}/revoke")
    public ApiResponse<Void> revokeAnnouncement(@PathVariable("id") String id) {
        announcementService.revoke(Long.parseLong(id));
        return ApiResponse.ok("已下架", null);
    }

    @PostMapping("/tenants/{id}/renew")
    public ApiResponse<Map<String, Object>> renew(
        @PathVariable("id") String id,
        @RequestBody RenewTenantRequest request
    ) {
        return ApiResponse.ok("续期成功", billingService.renew(Long.parseLong(id), request));
    }

    @PutMapping("/tenants/{id}/write-mode")
    public ApiResponse<Map<String, Object>> writeMode(
        @PathVariable("id") String id,
        @RequestBody WriteModeRequest request
    ) {
        if (request == null || request.getWriteMode() == null) {
            throw new IllegalArgumentException("请提供 writeMode");
        }
        return ApiResponse.ok("写模式已更新", billingService.setWriteMode(Long.parseLong(id), request.getWriteMode()));
    }

    @GetMapping("/billings")
    public ApiResponse<List<Map<String, Object>>> billings(@RequestParam(defaultValue = "100") int limit) {
        return ApiResponse.ok(billingService.listAll(limit));
    }

    @GetMapping("/tenants/{id}/billings")
    public ApiResponse<List<Map<String, Object>>> tenantBillings(@PathVariable("id") String id) {
        return ApiResponse.ok(billingService.listByTenant(Long.parseLong(id)));
    }
}
