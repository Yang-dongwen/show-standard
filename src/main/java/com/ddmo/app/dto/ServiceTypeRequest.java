package com.ddmo.app.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class ServiceTypeRequest {

    public static final int NAME_MAX = 32;

    @NotBlank(message = "服务名称不能为空")
    @Size(max = NAME_MAX, message = "服务名称最多" + NAME_MAX + "个字")
    private String name;

    @NotNull(message = "价格不能为空")
    @DecimalMin(value = "0", message = "价格必须大于等于0")
    @DecimalMax(value = "99999999.99", message = "价格不能超过99999999.99")
    @Digits(integer = 8, fraction = 2, message = "价格最多8位整数、2位小数")
    private BigDecimal price;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }
}
