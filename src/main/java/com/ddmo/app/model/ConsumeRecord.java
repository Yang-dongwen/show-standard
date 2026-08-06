package com.ddmo.app.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ConsumeRecord {
    private String id;
    private String customerId;
    private String employeeId;
    private String serviceTypeId;
    private BigDecimal amount;
    private String remark;
    private String status;
    private String relatedId;
    private LocalDateTime createdAt;

    public ConsumeRecord() {
    }

    public ConsumeRecord(String id, String customerId, String employeeId, String serviceTypeId,
                         BigDecimal amount, String remark, LocalDateTime createdAt) {
        this(id, customerId, employeeId, serviceTypeId, amount, remark, "normal", null, createdAt);
    }

    public ConsumeRecord(String id, String customerId, String employeeId, String serviceTypeId,
                         BigDecimal amount, String remark, String status, String relatedId, LocalDateTime createdAt) {
        this.id = id;
        this.customerId = customerId;
        this.employeeId = employeeId;
        this.serviceTypeId = serviceTypeId;
        this.amount = amount;
        this.remark = remark;
        this.status = status;
        this.relatedId = relatedId;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRelatedId() {
        return relatedId;
    }

    public void setRelatedId(String relatedId) {
        this.relatedId = relatedId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
