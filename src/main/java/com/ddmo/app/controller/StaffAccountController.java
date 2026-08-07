package com.ddmo.app.controller;

import com.ddmo.app.dto.ApiResponse;
import com.ddmo.app.dto.StaffAccountRequest;
import com.ddmo.app.dto.StaffResetPasswordRequest;
import com.ddmo.app.service.StaffAccountService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/staff")
public class StaffAccountController {

    private final StaffAccountService staffAccountService;

    public StaffAccountController(StaffAccountService staffAccountService) {
        this.staffAccountService = staffAccountService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.ok(staffAccountService.list());
    }

    @GetMapping("/roles")
    public ApiResponse<Map<String, Object>> roles() {
        return ApiResponse.ok(staffAccountService.rolesCatalog());
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody StaffAccountRequest request) {
        return ApiResponse.ok("账号已创建", staffAccountService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> update(
        @PathVariable String id,
        @RequestBody StaffAccountRequest request
    ) {
        return ApiResponse.ok("已更新", staffAccountService.update(parseId(id), request));
    }

    @PostMapping("/{id}/reset-password")
    public ApiResponse<Void> resetPassword(
        @PathVariable String id,
        @RequestBody StaffResetPasswordRequest request
    ) {
        String pwd = request == null ? null : request.getNewPassword();
        staffAccountService.resetPassword(parseId(id), pwd);
        return ApiResponse.ok("密码已重置", null);
    }

    @PatchMapping("/{id}/toggle-status")
    public ApiResponse<Map<String, Object>> toggleStatus(@PathVariable String id) {
        return ApiResponse.ok("状态已更新", staffAccountService.toggleStatus(parseId(id)));
    }

    private static long parseId(String id) {
        try {
            return Long.parseLong(id);
        } catch (Exception e) {
            throw new IllegalArgumentException("无效的账号 ID");
        }
    }
}
