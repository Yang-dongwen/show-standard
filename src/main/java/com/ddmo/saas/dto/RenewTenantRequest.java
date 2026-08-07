package com.ddmo.saas.dto;

import java.math.BigDecimal;

public class RenewTenantRequest {
    /** 续期天数，必填 >0 */
    private Integer days;
    private String planCode;
    private BigDecimal amount;
    private String note;
    /** 续期后是否强制 normal 写模式 */
    private Boolean clearReadonly = true;

    public Integer getDays() {
        return days;
    }

    public void setDays(Integer days) {
        this.days = days;
    }

    public String getPlanCode() {
        return planCode;
    }

    public void setPlanCode(String planCode) {
        this.planCode = planCode;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Boolean getClearReadonly() {
        return clearReadonly;
    }

    public void setClearReadonly(Boolean clearReadonly) {
        this.clearReadonly = clearReadonly;
    }
}
