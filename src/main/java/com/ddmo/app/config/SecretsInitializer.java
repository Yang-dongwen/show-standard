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
 * 首次启动在 ~/.show/secrets.properties 生成 JWT/AES 密钥并覆盖配置占位值。
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
            String secret = props.getProperty(JWT_KEY);
            if (secret == null || secret.isBlank() || isBootstrapPlaceholder(secret)) {
                secret = randomHex(48);
                props.setProperty(JWT_KEY, secret);
                dirty = true;
            }

            String aes = props.getProperty(AES_KEY);
            if (aes == null || aes.isBlank() || isBootstrapPlaceholder(aes)) {
                aes = randomHex(32);
                props.setProperty(AES_KEY, aes);
                dirty = true;
            }

            if (dirty || !Files.exists(secretsFile)) {
                try (OutputStream out = Files.newOutputStream(secretsFile)) {
                    props.store(out, "Show local secrets — do not share or commit");
                }
                log.info("已写入本地密钥文件: {}", secretsFile);
            }

            jwtProperties.setSecret(props.getProperty(JWT_KEY));
            jwtProperties.setTenantAesKey(props.getProperty(AES_KEY));
        } catch (IOException e) {
            throw new IllegalStateException("初始化本地密钥失败: " + e.getMessage(), e);
        }
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
