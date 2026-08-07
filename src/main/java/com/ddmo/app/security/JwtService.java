package com.ddmo.app.security;

import com.ddmo.app.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class JwtService {

    public static final String TYP_MANAGER = "manager";
    /** SaaS 平台运营 Token 类型 */
    public static final String TYP_SAAS = "saas";
    /** 兼容早期 platform 命名 */
    public static final String TYP_PLATFORM = "platform";
    /** 微信绑定前临时会话（仅含 openid，短有效期） */
    public static final String TYP_WX_PRE = "wx_pre";

    private final JwtProperties jwtProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    public String generateToken(String username, long tenantId) {
        return generateToken(username, tenantId, TYP_MANAGER, null, null);
    }

    public String generateToken(String username, long tenantId, long managerId, String role) {
        return generateToken(username, tenantId, TYP_MANAGER, managerId, role);
    }

    public String generateSaasToken(String username) {
        Instant now = Instant.now();
        Map<String, Object> claims = new HashMap<>();
        claims.put("typ", TYP_SAAS);
        return Jwts.builder()
            .subject(username)
            .claims(claims)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(jwtProperties.getExpireMinutes() * 60)))
            .signWith(signKey())
            .compact();
    }

    /** @deprecated 使用 {@link #generateSaasToken(String)} */
    @Deprecated
    public String generatePlatformToken(String username) {
        return generateSaasToken(username);
    }

    public String generateToken(String username, long tenantId, String typ) {
        return generateToken(username, tenantId, typ, null, null);
    }

    public String generateToken(String username, long tenantId, String typ, Long managerId, String role) {
        String encryptedTenantId = encryptTenantId(String.valueOf(tenantId));
        Instant now = Instant.now();
        Map<String, Object> claims = new HashMap<>();
        claims.put("tid_enc", encryptedTenantId);
        claims.put("typ", typ == null || typ.isBlank() ? TYP_MANAGER : typ);
        if (managerId != null) {
            claims.put("mid", managerId);
        }
        if (role != null && !role.isBlank()) {
            claims.put("role", role);
        }
        return Jwts.builder()
            .subject(username)
            .claims(claims)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(jwtProperties.getExpireMinutes() * 60)))
            .signWith(signKey())
            .compact();
    }

    public Long parseManagerId(String token) {
        Claims claims = parseClaims(token);
        Object mid = claims.get("mid");
        if (mid == null) {
            return null;
        }
        if (mid instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(mid));
        } catch (Exception e) {
            return null;
        }
    }

    public String parseRole(String token) {
        Claims claims = parseClaims(token);
        String role = claims.get("role", String.class);
        if (role == null || role.isBlank()) {
            return StaffRole.OWNER.code();
        }
        return role;
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
            .verifyWith(signKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public String parseTyp(String token) {
        Claims claims = parseClaims(token);
        String typ = claims.get("typ", String.class);
        if (typ == null || typ.isBlank()) {
            // 兼容旧 token（无 typ）
            return TYP_MANAGER;
        }
        return typ;
    }

    public String parseSubject(String token) {
        return parseClaims(token).getSubject();
    }

    /** 微信 code2Session 后、尚未绑定店长时的临时凭证（15 分钟） */
    public String generateWxPreToken(String openid) {
        Instant now = Instant.now();
        Map<String, Object> claims = new HashMap<>();
        claims.put("typ", TYP_WX_PRE);
        claims.put("openid", openid);
        return Jwts.builder()
            .subject(openid)
            .claims(claims)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(15 * 60)))
            .signWith(signKey())
            .compact();
    }

    public String parseWxPreOpenid(String preToken) {
        Claims claims = parseClaims(preToken);
        if (!TYP_WX_PRE.equals(claims.get("typ", String.class))) {
            throw new IllegalArgumentException("无效的微信预登录凭证");
        }
        String openid = claims.get("openid", String.class);
        if (openid == null || openid.isBlank()) {
            openid = claims.getSubject();
        }
        if (openid == null || openid.isBlank()) {
            throw new IllegalArgumentException("预登录凭证缺少 openid");
        }
        return openid;
    }

    public long parseTenantId(String token) {
        Claims claims = parseClaims(token);
        String typ = claims.get("typ", String.class);
        if (TYP_SAAS.equals(typ) || TYP_PLATFORM.equals(typ) || TYP_WX_PRE.equals(typ)) {
            throw new IllegalArgumentException("该令牌不含租户信息");
        }
        String encryptedTenantId = claims.get("tid_enc", String.class);
        if (encryptedTenantId == null || encryptedTenantId.isBlank()) {
            throw new IllegalArgumentException("token 缺少租户信息");
        }
        return Long.parseLong(decryptTenantId(encryptedTenantId));
    }

    private SecretKey signKey() {
        byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private String encryptTenantId(String tenantId) {
        try {
            byte[] iv = new byte[12];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey(), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(tenantId.getBytes(StandardCharsets.UTF_8));
            byte[] packed = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, packed, 0, iv.length);
            System.arraycopy(encrypted, 0, packed, iv.length, encrypted.length);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(packed);
        } catch (Exception e) {
            throw new IllegalStateException("租户ID加密失败", e);
        }
    }

    private String decryptTenantId(String encryptedText) {
        try {
            byte[] packed = Base64.getUrlDecoder().decode(encryptedText);
            byte[] iv = new byte[12];
            byte[] encrypted = new byte[packed.length - 12];
            System.arraycopy(packed, 0, iv, 0, 12);
            System.arraycopy(packed, 12, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, aesKey(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalArgumentException("token 中租户信息非法");
        }
    }

    private SecretKeySpec aesKey() {
        try {
            byte[] raw = jwtProperties.getTenantAesKey().getBytes(StandardCharsets.UTF_8);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw);
            byte[] key = new byte[16];
            System.arraycopy(digest, 0, key, 0, 16);
            return new SecretKeySpec(key, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("AES密钥生成失败", e);
        }
    }
}
