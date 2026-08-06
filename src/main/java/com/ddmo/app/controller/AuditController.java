package com.ddmo.app.controller;

import com.ddmo.app.dto.ApiResponse;
import com.ddmo.app.service.BarbershopService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final BarbershopService barbershopService;

    public AuditController(BarbershopService barbershopService) {
        this.barbershopService = barbershopService;
    }

    /**
     * 审计日志。传 page 时返回分页结构；否则保持旧版全量列表以兼容旧前端。
     */
    @GetMapping("/logs")
    public ApiResponse<?> logs(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Integer page,
        @RequestParam(defaultValue = "10") int size
    ) {
        if (page == null) {
            return ApiResponse.ok(barbershopService.listAuditLogs(keyword));
        }
        return ApiResponse.ok(barbershopService.listAuditLogsPaged(keyword, page, size));
    }
}
