package com.ddmo.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RegisterRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 32, message = "用户名长度须为3-32位")
    @Pattern(
        regexp = "^[a-zA-Z][a-zA-Z0-9_]{2,31}$",
        message = "用户名须字母开头，仅含字母/数字/下划线，不可中文或特殊符号"
    )
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度须为6-64位")
    private String password;

    @NotBlank(message = "昵称不能为空")
    @Size(max = 6, message = "昵称最多6个字")
    private String nickname;

    /** 当 app.register.mode=invite 时必填（C 端静态码） */
    @Size(max = 64, message = "邀请码过长")
    private String inviteCode;

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

    public String getInviteCode() {
        return inviteCode;
    }

    public void setInviteCode(String inviteCode) {
        this.inviteCode = inviteCode;
    }
}
