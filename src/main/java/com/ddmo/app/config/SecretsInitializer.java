package com.ddmo.app.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Properties;

/**
 * 解析并持久化 JWT/AES 密钥到 ~/.show/secrets.properties。
 * <p>
 * 优先级（多 EC2 / 生产 app.env）：
 * <ol>
 *   <li>JwtProperties 已绑定非占位值（APP_JWT_* / yml）→ 作为运行时真相；
 *       仅在 secrets 文件缺失或仍是占位时写入，不随机覆盖。</li>
 *   <li>否则使用 secrets.properties 中的可用值。</li>
 *   <li>仍无可用值时才随机生成（本地 desktop/cloud 首次启动）。</li>
 * </ol>
 * 全局 lazy-init 下必须 eager，确保密钥在签发 JWT 前写入 JwtProperties。
 */
@Component
@Lazy(false)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecretsInitializer {

    private static final Logger log = LoggerFactory.getLogger(SecretsInitializer.class);
    private static final String JWT_KEY = "jwt.secret";
    private static final String AES_KEY = "jwt.tenant-aes-key";

    private final JwtProperties jwtProperties;

    public SecretsInitializer(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    @PostConstruct
    public void init() {
        try {
            Path dir = Path.of(System.getProperty("user.home"), ".show");
            Files.createDirectories(dir);
            Path secretsFile = dir.resolve("secrets.properties");

            Properties props = new Properties();
            if (Files.exists(secretsFile)) {
                try (InputStream in = Files.newInputStream(secretsFile)) {
                    props.load(in);
                }
            }

            boolean dirty = false;

            String resolvedSecret = resolve(
                    jwtProperties.getSecret(),
                    props.getProperty(JWT_KEY),
                    48);
            if (isUsable(jwtProperties.getSecret()) && isMissingOrPlaceholder(props.getProperty(JWT_KEY))) {
                props.setProperty(JWT_KEY, resolvedSecret);
                dirty = true;
            } else if (!isUsable(jwtProperties.getSecret())) {
                // file 或 random：确保落盘
                if (!resolvedSecret.equals(nullToEmpty(props.getProperty(JWT_KEY)))) {
                    props.setProperty(JWT_KEY, resolvedSecret);
                    dirty = true;
                }
            }

            String resolvedAes = resolve(
                    jwtProperties.getTenantAesKey(),
                    props.getProperty(AES_KEY),
                    32);
            if (isUsable(jwtProperties.getTenantAesKey()) && isMissingOrPlaceholder(props.getProperty(AES_KEY))) {
                props.setProperty(AES_KEY, resolvedAes);
                dirty = true;
            } else if (!isUsable(jwtProperties.getTenantAesKey())) {
                if (!resolvedAes.equals(nullToEmpty(props.getProperty(AES_KEY)))) {
                    props.setProperty(AES_KEY, resolvedAes);
                    dirty = true;
                }
            }

            if (dirty || !Files.exists(secretsFile)) {
                // 随机生成时 props 可能尚未写入 resolved（上面已 set）；再兜一次
                if (isMissingOrPlaceholder(props.getProperty(JWT_KEY))) {
                    props.setProperty(JWT_KEY, resolvedSecret);
                }
                if (isMissingOrPlaceholder(props.getProperty(AES_KEY))) {
                    props.setProperty(AES_KEY, resolvedAes);
                }
                try (OutputStream out = Files.newOutputStream(secretsFile)) {
                    props.store(out, "Show local secrets — do not share or commit");
                }
                log.info("已写入本地密钥文件: {}", secretsFile);
            }

            // 运行时始终采用 resolved（env/yml 优先 → file → random）
            jwtProperties.setSecret(resolvedSecret);
            jwtProperties.setTenantAesKey(resolvedAes);
        } catch (IOException e) {
            throw new IllegalStateException("初始化本地密钥失败: " + e.getMessage(), e);
        }
    }

    /** env/yml 可用 → 用之；否则 file 可用 → 用之；否则随机。 */
    private static String resolve(String fromBound, String fromFile, int randomBytes) {
        if (isUsable(fromBound)) {
            return fromBound;
        }
        if (isUsable(fromFile)) {
            return fromFile;
        }
        return randomHex(randomBytes);
    }

    private static boolean isUsable(String value) {
        return value != null && !value.isBlank() && !isBootstrapPlaceholder(value);
    }

    private static boolean isMissingOrPlaceholder(String value) {
        return value == null || value.isBlank() || isBootstrapPlaceholder(value);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static boolean isBootstrapPlaceholder(String value) {
        return value.contains("bootstrap") || value.contains("placeholder") || value.contains("change-me");
    }

    private static String randomHex(int bytes) {
        byte[] buf = new byte[bytes];
        new SecureRandom().nextBytes(buf);
        return HexFormat.of().formatHex(buf);
    }
}
