package com.ddmo.saas.service;

import com.ddmo.app.util.SnowflakeIdGenerator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SaasInviteService {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final JdbcTemplate jdbcTemplate;
    private final SnowflakeIdGenerator idGenerator;
    private final SecureRandom random = new SecureRandom();

    public SaasInviteService(JdbcTemplate jdbcTemplate, SnowflakeIdGenerator idGenerator) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = idGenerator;
    }

    public long assertUsable(String providedCode, String staticFallbackCode) {
        String provided = providedCode == null ? "" : providedCode.trim();
        if (provided.isEmpty()) {
            throw new IllegalArgumentException("请输入邀请码");
        }
        String staticCode = staticFallbackCode == null ? "" : staticFallbackCode.trim();
        if (!staticCode.isEmpty() && staticCode.equals(provided)) {
            return -1L;
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT id, max_uses, used_count, status, expire_at FROM t_invite_code WHERE code = ?",
            provided
        );
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("邀请码无效");
        }
        Map<String, Object> row = rows.get(0);
        if (!"active".equals(String.valueOf(row.get("status")))) {
            throw new IllegalArgumentException("邀请码已失效");
        }
        if (isExpired(row.get("expire_at"))) {
            throw new IllegalArgumentException("邀请码已过期");
        }
        int maxUses = ((Number) row.get("max_uses")).intValue();
        int used = ((Number) row.get("used_count")).intValue();
        if (used >= maxUses) {
            throw new IllegalArgumentException("邀请码已用尽");
        }
        return ((Number) row.get("id")).longValue();
    }

    private boolean isExpired(Object exp) {
        if (exp == null) {
            return false;
        }
        try {
            Instant expireAt;
            if (exp instanceof Timestamp ts) {
                expireAt = ts.toInstant();
            } else if (exp instanceof java.util.Date d) {
                expireAt = d.toInstant();
            } else {
                String s = String.valueOf(exp).trim().replace('T', ' ');
                if (s.length() >= 19) {
                    s = s.substring(0, 19);
                }
                expireAt = Timestamp.valueOf(s).toInstant();
            }
            return Instant.now().isAfter(expireAt);
        } catch (Exception e) {
            return false;
        }
    }

    @Transactional
    public void consume(long inviteId) {
        if (inviteId <= 0) {
            return;
        }
        int updated = jdbcTemplate.update("""
                UPDATE t_invite_code
                SET used_count = used_count + 1, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'active' AND used_count < max_uses
                """,
            inviteId
        );
        if (updated == 0) {
            throw new IllegalArgumentException("邀请码已用尽或无效");
        }
        jdbcTemplate.update("""
                UPDATE t_invite_code SET status = 'exhausted', updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND used_count >= max_uses AND status = 'active'
                """,
            inviteId
        );
    }

    public List<Map<String, Object>> listAll() {
        return jdbcTemplate.queryForList("""
            SELECT id, code, max_uses, used_count, status, expire_at, note, created_at
            FROM t_invite_code ORDER BY created_at DESC
            """
        ).stream().map(this::stringifyIds).collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> create(Integer maxUses, String note, Integer expireDays) {
        int uses = maxUses == null || maxUses < 1 ? 1 : maxUses;
        String code = generateCode(8);
        long id = idGenerator.nextId();
        Timestamp expireAt = null;
        if (expireDays != null && expireDays > 0) {
            expireAt = Timestamp.from(Instant.now().plusSeconds(expireDays * 86400L));
        }
        String noteVal = note == null ? "" : note.trim();
        jdbcTemplate.update("""
                INSERT INTO t_invite_code(id, code, max_uses, used_count, status, expire_at, note, created_at, updated_at)
                VALUES (?, ?, ?, 0, 'active', ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
            id, code, uses, expireAt, noteVal
        );
        Map<String, Object> result = new HashMap<>();
        result.put("id", String.valueOf(id));
        result.put("code", code);
        result.put("maxUses", uses);
        result.put("note", noteVal);
        result.put("expireAt", expireAt);
        return result;
    }

    @Transactional
    public void revoke(long id) {
        int n = jdbcTemplate.update(
            "UPDATE t_invite_code SET status = 'revoked', updated_at = CURRENT_TIMESTAMP WHERE id = ?",
            id
        );
        if (n == 0) {
            throw new IllegalArgumentException("邀请码不存在");
        }
    }

    private String generateCode(int len) {
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder sb = new StringBuilder(len);
            for (int i = 0; i < len; i++) {
                sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }
            String code = sb.toString();
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM t_invite_code WHERE code = ?", Integer.class, code
            );
            if (count == null || count == 0) {
                return code;
            }
        }
        throw new IllegalStateException("无法生成唯一邀请码");
    }

    private Map<String, Object> stringifyIds(Map<String, Object> row) {
        Map<String, Object> m = new HashMap<>(row);
        if (m.get("id") != null) {
            m.put("id", String.valueOf(m.get("id")));
        }
        return m;
    }
}
