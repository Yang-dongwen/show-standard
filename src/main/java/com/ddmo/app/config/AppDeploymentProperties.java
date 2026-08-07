package com.ddmo.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 产品双轨交付：
 * <ul>
 *   <li>{@code desktop} — 纯本地买断：SQLite，无云库、无小程序、无 SaaS 运营台</li>
 *   <li>{@code cloud} — SaaS 订阅：云端 MySQL，SaaS 运营 + 商家小程序</li>
 * </ul>
 */
@Component
@ConfigurationProperties(prefix = "app")
public class AppDeploymentProperties {

    /** desktop | cloud */
    private String deployment = "desktop";

    private final Wx wx = new Wx();

    public String getDeployment() {
        return deployment;
    }

    public void setDeployment(String deployment) {
        this.deployment = deployment;
    }

    public boolean isCloud() {
        return "cloud".equalsIgnoreCase(deployment == null ? "" : deployment.trim());
    }

    public boolean isDesktop() {
        return !isCloud();
    }

    /** 是否启用 SaaS 运营 API/台（仅 cloud） */
    public boolean isSaasEnabled() {
        return isCloud();
    }

    /** 客户可见产品代号：local | saas */
    public String getEdition() {
        return isCloud() ? "saas" : "local";
    }

    /** 商业模式：buyout 一次性买断 | subscription 订阅 */
    public String getLicenseModel() {
        return isCloud() ? "subscription" : "buyout";
    }

    public String getEditionLabel() {
        return isCloud() ? "SaaS 云版" : "本地买断版";
    }

    public Wx getWx() {
        return wx;
    }

    public static class Wx {
        private final Miniapp miniapp = new Miniapp();

        public Miniapp getMiniapp() {
            return miniapp;
        }
    }

    public static class Miniapp {
        private boolean enabled = false;
        private String appId = "";
        private String appSecret = "";
        private boolean mock = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getAppId() {
            return appId;
        }

        public void setAppId(String appId) {
            this.appId = appId;
        }

        public String getAppSecret() {
            return appSecret;
        }

        public void setAppSecret(String appSecret) {
            this.appSecret = appSecret;
        }

        public boolean isMock() {
            return mock;
        }

        public void setMock(boolean mock) {
            this.mock = mock;
        }
    }
}
