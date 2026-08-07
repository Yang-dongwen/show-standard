package com.ddmo.app.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class ConsumeRequest {

    public static final int REMARK_MAX = 200;

    @NotBlank(message = "会员不能为空")
    private String customerId;

    @NotBlank(message = "员工不能为空")
    private String employeeId;

    @NotBlank(message = "服务不能为空")
    private String serviceTypeId;

    @NotBlank(message = "请输入4位校验码")
    @Pattern(regexp = "\\d{4}", message = "校验码必须是4位数字")
    private String verifyCode;

    @NotNull(message = "消费金额不能为空")
    @DecimalMin(value = "0.01", message = "消费金额必须大于0")
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

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public String getServiceTypeId() {
        return serviceTypeId;
    }

    public void setServiceTypeId(String serviceTypeId) {
        this.serviceTypeId = serviceTypeId;
    }

    public String getVerifyCode() {
        return verifyCode;
    }

    public void setVerifyCode(String verifyCode) {
        this.verifyCode = verifyCode;
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
