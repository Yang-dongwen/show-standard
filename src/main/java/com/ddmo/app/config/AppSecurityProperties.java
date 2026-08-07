package com.ddmo.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 安全相关开关。cloud 生产请设 {@code strict-cloud=true}。
 */
@Component
@ConfigurationProperties(prefix = "app.security")
public class AppSecurityProperties {

    /**
     * 为 true 时：cloud 禁止微信 mock、禁止 demo 密钥、禁止弱 SaaS 引导密码。
     * 本地/联调默认 false。
     */
    private boolean strictCloud = false;

    public boolean isStrictCloud() {
        return strictCloud;
    }

    public void setStrictCloud(boolean strictCloud) {
        this.strictCloud = strictCloud;
    }
}
