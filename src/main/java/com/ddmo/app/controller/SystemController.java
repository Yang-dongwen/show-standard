package com.ddmo.app.controller;

import com.ddmo.app.config.AppDeploymentProperties;
import com.ddmo.app.config.DbDialect;
import com.ddmo.app.dto.ApiResponse;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final ApplicationContext applicationContext;
    private final Environment environment;
    private final AppDeploymentProperties deploymentProperties;
    private final DbDialect dbDialect;

    public SystemController(
        ApplicationContext applicationContext,
        Environment environment,
        AppDeploymentProperties deploymentProperties,
        DbDialect dbDialect
    ) {
        this.applicationContext = applicationContext;
        this.environment = environment;
        this.deploymentProperties = deploymentProperties;
        this.dbDialect = dbDialect;
    }

    @GetMapping("/access-info")
    public ApiResponse<Map<String, Object>> accessInfo() {
        int port = resolveServerPort();
        String ip = resolveHostIp();
        Map<String, Object> data = new HashMap<>();
        data.put("ip", ip);
        data.put("port", port);
        data.put("url", "http://" + ip + ":" + port);
        data.put("deployment", deploymentProperties.getDeployment());
        data.put("edition", deploymentProperties.getEdition());
        data.put("editionLabel", deploymentProperties.getEditionLabel());
        data.put("licenseModel", deploymentProperties.getLicenseModel());
        data.put("db", dbDialect.label());
        data.put("saasEnabled", deploymentProperties.isSaasEnabled());
        data.put("miniProgramEnabled", miniProgramActuallyEnabled());
        return ApiResponse.ok(data);
    }

    /**
     * 产品线：local 买断（SQLite） vs saas 云订阅（MySQL）。
     */
    @GetMapping("/product-line")
    public ApiResponse<Map<String, Object>> productLine() {
        Map<String, Object> data = new HashMap<>();
        data.put("deployment", deploymentProperties.getDeployment());
        data.put("edition", deploymentProperties.getEdition());
        data.put("editionLabel", deploymentProperties.getEditionLabel());
        data.put("licenseModel", deploymentProperties.getLicenseModel());
        data.put("line", deploymentProperties.isCloud() ? "saas" : "local");
        data.put("localData", deploymentProperties.isDesktop());
        data.put("db", dbDialect.label());
        data.put("dbFileMode", dbDialect.isFileDatabase());
        data.put("saasEnabled", deploymentProperties.isSaasEnabled());
        data.put("requiresSaasForMiniProgram", true);

        Map<String, Object> localSku = new HashMap<>();
        localSku.put("id", "local");
        localSku.put("name", "本地买断版");
        localSku.put("licenseModel", "buyout");
        localSku.put("db", "sqlite");
        localSku.put("install", "桌面 MSI/本机 jar");
        localSku.put("features", List.of("门店会员收银", "本机 SQLite", "离线可用", "整库文件备份"));
        localSku.put("notIncluded", List.of("云端 MySQL", "SaaS 运营台", "微信小程序", "多店平台开通"));

        Map<String, Object> saasSku = new HashMap<>();
        saasSku.put("id", "saas");
        saasSku.put("name", "SaaS 云版");
        saasSku.put("licenseModel", "subscription");
        saasSku.put("db", "mysql");
        saasSku.put("install", "云服务器 / Docker（spring.profiles.active=cloud）");
        saasSku.put("features", List.of("云端 MySQL", "SaaS 运营台", "邀请开店", "商家小程序", "到期/只读管控"));
        saasSku.put("notIncluded", List.of("纯离线单机买断（请用本地版）"));

        data.put("skus", List.of(localSku, saasSku));
        data.put("activeSku", deploymentProperties.isCloud() ? saasSku : localSku);

        Map<String, Object> schemes = new HashMap<>();
        schemes.put("local", "sqlite");
        schemes.put("saas", "mysql");
        data.put("dbSchemes", schemes);

        Map<String, Object> mp = new HashMap<>();
        mp.put("enabled", miniProgramActuallyEnabled());
        mp.put("side", "merchant");
        mp.put("note", deploymentProperties.isDesktop()
            ? "本地买断版不支持小程序；需 SaaS 云版"
            : "V1 仅商家侧");
        data.put("miniProgram", mp);
        return ApiResponse.ok(data);
    }

    private boolean miniProgramActuallyEnabled() {
        return deploymentProperties.isCloud()
            && deploymentProperties.getWx().getMiniapp().isEnabled();
    }

    /** MockMvc 测试环境无真实 WebServer，回退到 server.port 配置。 */
    private int resolveServerPort() {
        if (applicationContext instanceof ServletWebServerApplicationContext web) {
            try {
                return web.getWebServer().getPort();
            } catch (Exception ignored) {
                // fall through
            }
        }
        return environment.getProperty("local.server.port", Integer.class,
            environment.getProperty("server.port", Integer.class, 8080));
    }

    private String resolveHostIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface netIf = interfaces.nextElement();
                if (!netIf.isUp() || netIf.isLoopback() || netIf.isVirtual()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = netIf.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception ignored) {
            return "127.0.0.1";
        }
    }
}
