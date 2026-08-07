package com.ddmo.saas.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.saas")
public class AppSaasProperties {

    /**
     * 是否允许启动时在空库创建 SaaS 引导管理员。
     * 生产建议 false，改为人工建号。
     */
    private boolean bootstrapEnabled = true;
    private String bootstrapUsername = "platform";
    private String bootstrapPassword = "";
    /** 开店静态邀请码兜底（可选，优先库表 t_invite_code） */
    private String inviteCode = "";

    public boolean isBootstrapEnabled() {
        return bootstrapEnabled;
    }

    public void setBootstrapEnabled(boolean bootstrapEnabled) {
        this.bootstrapEnabled = bootstrapEnabled;
    }

    public String getBootstrapUsername() {
        return bootstrapUsername;
    }

    public void setBootstrapUsername(String bootstrapUsername) {
        this.bootstrapUsername = bootstrapUsername;
    }

    public String getBootstrapPassword() {
        return bootstrapPassword;
    }

    public void setBootstrapPassword(String bootstrapPassword) {
        this.bootstrapPassword = bootstrapPassword;
    }

    public String getInviteCode() {
        return inviteCode;
    }

    public void setInviteCode(String inviteCode) {
        this.inviteCode = inviteCode;
    }
}
