package com.ddmo.app.controller;

import com.ddmo.app.dto.ApiResponse;
import com.ddmo.app.security.TenantContext;
import com.ddmo.saas.service.SaasAnnouncementService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * C 端读取平台公告（只读，数据由 SaaS 发布）。
 */
@RestController
@RequestMapping("/api/announcements")
public class AnnouncementController {

    private final SaasAnnouncementService announcementService;

    public AnnouncementController(SaasAnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        long tenantId = TenantContext.getTenantId();
        return ApiResponse.ok(announcementService.listForTenant(tenantId));
    }
}
