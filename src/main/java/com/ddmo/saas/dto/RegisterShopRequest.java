package com.ddmo.saas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** SaaS 公开开店：邀请码 + 店长账号，写入共享库 C 端表 */
public class RegisterShopRequest {

    @NotBlank(message = "请输入邀请码")
    @Size(max = 64, message = "邀请码过长")
    private String inviteCode;

    @NotBlank(message = "用户名不能为空")
    @Size(max = 32, message = "用户名过长")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度须为6-64位")
    private String password;

    @NotBlank(message = "昵称不能为空")
    @Size(max = 6, message = "昵称最多6个字")
    private String nickname;

    @Size(max = 64, message = "门店名称最多64字")
    private String shopName;

    public String getInviteCode() {
        return inviteCode;
    }

    public void setInviteCode(String inviteCode) {
        this.inviteCode = inviteCode;
    }

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

    public String getShopName() {
        return shopName;
    }

    public void setShopName(String shopName) {
        this.shopName = shopName;
    }
}
