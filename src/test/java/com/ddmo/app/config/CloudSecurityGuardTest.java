package com.ddmo.app.config;

import com.ddmo.saas.config.AppSaasProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CloudSecurityGuardTest {

    @Test
    void desktopSkipsChecks() {
        AppDeploymentProperties dep = new AppDeploymentProperties();
        dep.setDeployment("desktop");
        AppSecurityProperties sec = new AppSecurityProperties();
        sec.setStrictCloud(true);
        CloudSecurityGuard guard = new CloudSecurityGuard(dep, sec, new AppSaasProperties(), new JwtProperties());
        assertThatCode(() -> guard.run(new DefaultApplicationArguments()))
            .doesNotThrowAnyException();
    }

    @Test
    void strictCloudRejectsMock() {
        AppDeploymentProperties dep = new AppDeploymentProperties();
        dep.setDeployment("cloud");
        dep.getWx().getMiniapp().setMock(true);
        dep.getWx().getMiniapp().setEnabled(true);
        AppSecurityProperties sec = new AppSecurityProperties();
        sec.setStrictCloud(true);
        CloudSecurityGuard guard = new CloudSecurityGuard(dep, sec, new AppSaasProperties(), jwtOk());
        assertThatThrownBy(() -> guard.run(new DefaultApplicationArguments()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("mock");
    }

    @Test
    void strictCloudRejectsWeakBootstrapPassword() {
        AppDeploymentProperties dep = new AppDeploymentProperties();
        dep.setDeployment("cloud");
        dep.getWx().getMiniapp().setMock(false);
        dep.getWx().getMiniapp().setEnabled(false);
        AppSecurityProperties sec = new AppSecurityProperties();
        sec.setStrictCloud(true);
        AppSaasProperties saas = new AppSaasProperties();
        saas.setBootstrapEnabled(true);
        saas.setBootstrapPassword("platform123");
        CloudSecurityGuard guard = new CloudSecurityGuard(dep, sec, saas, jwtOk());
        assertThatThrownBy(() -> guard.run(new DefaultApplicationArguments()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("引导密码");
    }

    private static JwtProperties jwtOk() {
        JwtProperties jwt = new JwtProperties();
        jwt.setSecret("unit-test-secret-key-at-least-32-bytes-long!!");
        jwt.setTenantAesKey("unit-test-aes-key-32bytes-long!!");
        return jwt;
    }
}
