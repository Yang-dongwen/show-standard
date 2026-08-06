package com.ddmo.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.auth")
public class AppAuthProperties {

    /**
     * 是否允许校验历史明文密码并迁移为 BCrypt。
     * 新部署建议 false。
     */
    private boolean allowPlaintextPassword = false;

    public boolean isAllowPlaintextPassword() {
        return allowPlaintextPassword;
    }

    public void setAllowPlaintextPassword(boolean allowPlaintextPassword) {
        this.allowPlaintextPassword = allowPlaintextPassword;
    }
}
