package com.ddmo.app.controller;

import com.ddmo.app.dto.ApiResponse;
import com.ddmo.app.dto.SettingsUpdateRequest;
import com.ddmo.app.service.BarbershopService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final BarbershopService barbershopService;

    public SettingsController(BarbershopService barbershopService) {
        this.barbershopService = barbershopService;
    }

    @GetMapping
    public ApiResponse<Map<String, String>> get() {
        return ApiResponse.ok(barbershopService.getSettings());
    }

    @PutMapping
    public ApiResponse<Map<String, String>> update(@RequestBody SettingsUpdateRequest request) {
        return ApiResponse.ok("设置已保存", barbershopService.updateSettings(request.getSettings()));
    }
}
