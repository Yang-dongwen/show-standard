package com.ddmo.app.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Customer {
    private String id;
    private String name;
    private String phone;
    private String verifyCode;
    private String remark;
    private String status;
    private BigDecimal balance;
    private LocalDateTime createdAt;

    public Customer() {
    }

    public Customer(String id, String name, String phone, String verifyCode, String remark, String status, LocalDateTime createdAt) {
        this(id, name, phone, verifyCode, remark, status, null, createdAt);
    }

    public Customer(String id, String name, String phone, String verifyCode, String remark, String status,
                    BigDecimal balance, LocalDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.verifyCode = verifyCode;
        this.remark = remark;
        this.status = status;
        this.balance = balance;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
