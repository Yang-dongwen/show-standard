package com.ddmo.app.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class RechargeRequest {

    public static final int REMARK_MAX = 200;

    @NotBlank(message = "会员不能为空")
    private String customerId;

    @NotNull(message = "充值金额不能为空")
    @DecimalMin(value = "0.01", message = "充值金额必须大于0")
    @DecimalMax(value = "99999999.99", message = "金额不能超过99999999.99")
    @Digits(integer = 8, fraction = 2, message = "金额最多8位整数、2位小数")
    private BigDecimal amount;

    @Size(max = REMARK_MAX, message = "备注最多" + REMARK_MAX + "个字")
    private String remark;

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
