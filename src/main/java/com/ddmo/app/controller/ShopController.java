package com.ddmo.app.controller;

import com.ddmo.app.dto.ApiResponse;
import com.ddmo.app.dto.ShopUpdateRequest;
import com.ddmo.app.service.BarbershopService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/shop")
public class ShopController {

    private final BarbershopService barbershopService;

    public ShopController(BarbershopService barbershopService) {
        this.barbershopService = barbershopService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> get() {
        return ApiResponse.ok(barbershopService.getShopProfile());
    }

    @PutMapping
    public ApiResponse<Map<String, Object>> update(@Valid @RequestBody ShopUpdateRequest request) {
        return ApiResponse.ok("门店资料已保存", barbershopService.updateShopProfile(request.getShopName()));
    }
}
