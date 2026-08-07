package com.ddmo.app.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class CustomerRequest {

    public static final int NAME_MAX = 32;
    public static final int REMARK_MAX = 200;

    @NotBlank(message = "会员姓名不能为空")
    @Size(max = NAME_MAX, message = "会员姓名最多" + NAME_MAX + "个字")
    private String name;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "1\\d{10}", message = "手机号须为11位且以1开头")
    private String phone;

    /** 可选；非空时由业务层校验 4 位数字（允许省略，默认取手机后四位） */
    private String verifyCode;

    @DecimalMin(value = "0", message = "初次充值金额不能小于0")
    @DecimalMax(value = "99999999.99", message = "金额不能超过99999999.99")
    @Digits(integer = 8, fraction = 2, message = "金额最多8位整数、2位小数")
    private BigDecimal initialRechargeAmount;

    @Size(max = REMARK_MAX, message = "备注最多" + REMARK_MAX + "个字")
    private String remark;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getVerifyCode() {
        return verifyCode;
    }

    public void setVerifyCode(String verifyCode) {
        this.verifyCode = verifyCode;
    }

    public BigDecimal getInitialRechargeAmount() {
        return initialRechargeAmount;
    }

    public void setInitialRechargeAmount(BigDecimal initialRechargeAmount) {
        this.initialRechargeAmount = initialRechargeAmount;
    }
}
