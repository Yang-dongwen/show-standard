package com.ddmo.app.service;

import com.ddmo.app.config.AppDeploymentProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 微信小程序 code2Session。
 * mock=true 时 code 映射为固定 openid，便于本地联调。
 */
@Service
public class WxMiniProgramService {

    private static final Logger log = LoggerFactory.getLogger(WxMiniProgramService.class);

    private final AppDeploymentProperties deploymentProperties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    public WxMiniProgramService(AppDeploymentProperties deploymentProperties, ObjectMapper objectMapper) {
        this.deploymentProperties = deploymentProperties;
        this.objectMapper = objectMapper;
    }

    public void assertMiniappEnabled() {
        // 本地买断版永不开放小程序（即使 mock）
        if (deploymentProperties.isDesktop()) {
            throw new IllegalArgumentException(
                "本地买断版不支持微信小程序；请使用 SaaS 云版（云端 MySQL + cloud profile）"
            );
        }
        var mini = deploymentProperties.getWx().getMiniapp();
        // cloud 下 mock=true 允许联调；生产请 enabled=true 且 mock=false
        if (mini.isEnabled() || mini.isMock()) {
            return;
        }
        throw new IllegalArgumentException(
            "未开启小程序能力。SaaS 云版请设置 app.wx.miniapp.enabled=true；开发联调可设 mock=true。"
        );
    }

    /**
     * @return openid, optional unionid
     */
    public Map<String, String> code2Session(String code) {
        assertMiniappEnabled();
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code 不能为空");
        }
        String c = code.trim();
        var mini = deploymentProperties.getWx().getMiniapp();
        if (mini.isMock()) {
            Map<String, String> m = new HashMap<>();
            // 同一 code 稳定 openid，便于重复测试
            m.put("openid", "mock_openid_" + Integer.toHexString(c.hashCode()));
            m.put("unionid", "");
            m.put("session_key", "mock_session");
            log.debug("wx mock code2Session code={} openid={}", c, m.get("openid"));
            return m;
        }
        String appId = mini.getAppId();
        String secret = mini.getAppSecret();
        if (appId == null || appId.isBlank() || secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("未配置 app.wx.miniapp.app-id / app-secret");
        }
        try {
            String url = "https://api.weixin.qq.com/sns/jscode2session"
                + "?appid=" + URLEncoder.encode(appId, StandardCharsets.UTF_8)
                + "&secret=" + URLEncoder.encode(secret, StandardCharsets.UTF_8)
                + "&js_code=" + URLEncoder.encode(c, StandardCharsets.UTF_8)
                + "&grant_type=authorization_code";
            String body = restTemplate.getForObject(url, String.class);
            JsonNode node = objectMapper.readTree(body == null ? "{}" : body);
            if (node.has("errcode") && node.get("errcode").asInt() != 0) {
                throw new IllegalArgumentException(
                    "微信登录失败: " + node.path("errmsg").asText("unknown")
                );
            }
            String openid = node.path("openid").asText("");
            if (openid.isBlank()) {
                throw new IllegalArgumentException("微信未返回 openid");
            }
            Map<String, String> m = new HashMap<>();
            m.put("openid", openid);
            m.put("unionid", node.path("unionid").asText(""));
            m.put("session_key", node.path("session_key").asText(""));
            return m;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.warn("code2Session error", e);
            throw new IllegalArgumentException("调用微信接口失败: " + e.getMessage());
        }
    }

    public String appId() {
        return deploymentProperties.getWx().getMiniapp().getAppId();
    }
}
