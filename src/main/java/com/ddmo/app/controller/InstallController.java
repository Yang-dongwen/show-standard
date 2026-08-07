package com.ddmo.app.controller;

import com.ddmo.app.dto.ApiResponse;
import com.ddmo.app.install.InstallCompleteRequest;
import com.ddmo.app.install.InstallService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 安装向导公开 API（无需登录）。
 */
@RestController
@RequestMapping("/api/install")
public class InstallController {

    private final InstallService installService;

    public InstallController(InstallService installService) {
        this.installService = installService;
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        return ApiResponse.ok(installService.status());
    }

    @PostMapping("/test-mysql")
    public ApiResponse<Map<String, Object>> testMysql(@RequestBody InstallCompleteRequest request) {
        return ApiResponse.ok(installService.testMysql(request));
    }

    @PostMapping("/complete")
    public ApiResponse<Map<String, Object>> complete(@RequestBody InstallCompleteRequest request) {
        return ApiResponse.ok("安装配置已保存", installService.complete(request));
    }
}
