package com.ddmo.app.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class StaffAccountRequest {

    @Size(min = 3, max = 32, message = "用户名长度须为3-32位")
    @Pattern(
        regexp = "^[a-zA-Z][a-zA-Z0-9_]{2,31}$",
        message = "用户名须字母开头，仅含字母/数字/下划线，不可中文或特殊符号"
    )
    private String username;
    private String password;
    private String nickname;
    /** owner | cashier | staff */
    private String role;
    private String status;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
