package com.ddmo.app.config;

import com.ddmo.saas.config.AppSaasProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 云版启动安全检查：mock / 弱口令 / demo 密钥告警或拒绝启动。
 */
@Component
@Order(50)
public class CloudSecurityGuard implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CloudSecurityGuard.class);

    private final AppDeploymentProperties deploymentProperties;
    private final AppSecurityProperties securityProperties;
    private final AppSaasProperties saasProperties;
    private final JwtProperties jwtProperties;

    public CloudSecurityGuard(
        AppDeploymentProperties deploymentProperties,
        AppSecurityProperties securityProperties,
        AppSaasProperties saasProperties,
        JwtProperties jwtProperties
    ) {
        this.deploymentProperties = deploymentProperties;
        this.securityProperties = securityProperties;
        this.saasProperties = saasProperties;
        this.jwtProperties = jwtProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!deploymentProperties.isCloud()) {
            return;
        }

        var mini = deploymentProperties.getWx().getMiniapp();
        if (mini.isMock()) {
            log.warn("================================================================");
            log.warn("  CLOUD + app.wx.miniapp.mock=true");
            log.warn("  微信身份可被 mock，请勿用于生产公网。");
            log.warn("  生产请设置 mock=false，并配置真实 app-id/app-secret。");
            log.warn("  若要强制拒绝启动：app.security.strict-cloud=true");
            log.warn("================================================================");
        }

        if (!securityProperties.isStrictCloud()) {
            return;
        }

        if (mini.isMock()) {
            throw new IllegalStateException(
                "strict-cloud：禁止 app.wx.miniapp.mock=true（生产请关闭 mock）"
            );
        }
        if (mini.isEnabled()) {
            String appId = mini.getAppId() == null ? "" : mini.getAppId().trim();
            String secret = mini.getAppSecret() == null ? "" : mini.getAppSecret().trim();
            if (appId.isBlank() || secret.isBlank()
                || appId.contains("demo") || secret.contains("demo")) {
                throw new IllegalStateException(
                    "strict-cloud：请配置真实 app.wx.miniapp.app-id / app-secret（不可为 demo 占位）"
                );
            }
        }
        if (saasProperties.isBootstrapEnabled()) {
            String pwd = saasProperties.getBootstrapPassword();
            if (pwd != null && !pwd.isBlank() && isWeakPassword(pwd)) {
                throw new IllegalStateException(
                    "strict-cloud：禁止使用弱 SaaS 引导密码；请设置强密码或 bootstrap-enabled=false"
                );
            }
        }
        String jwt = jwtProperties.getSecret();
        if (jwt != null && (jwt.contains("bootstrap") || jwt.contains("placeholder") || jwt.contains("change-me"))) {
            throw new IllegalStateException(
                "strict-cloud：JWT secret 仍为占位值；请依赖 secrets 初始化或环境覆盖"
            );
        }
        log.info("strict-cloud 检查通过");
    }

    private static boolean isWeakPassword(String password) {
        String p = password.trim().toLowerCase();
        return p.length() < 10
            || "platform123".equals(p)
            || "password".equals(p)
            || "123456".equals(p)
            || "admin123".equals(p);
    }
}
