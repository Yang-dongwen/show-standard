package com.ddmo.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ShopUpdateRequest {

    @NotBlank(message = "门店名称不能为空")
    @Size(max = 64, message = "门店名称最多64字")
    private String shopName;

    public String getShopName() {
        return shopName;
    }

    public void setShopName(String shopName) {
        this.shopName = shopName;
    }
}
