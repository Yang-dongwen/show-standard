package com.ddmo.app.dto;

public class WxBindRequest {
    /** wx.login 的 code，或 wx-login 返回的 preToken 二选一 */
    private String code;
    private String preToken;
    private String username;
    private String password;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getPreToken() {
        return preToken;
    }

    public void setPreToken(String preToken) {
        this.preToken = preToken;
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
}
