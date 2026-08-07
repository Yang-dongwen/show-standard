package com.ddmo.app.service;

import com.ddmo.app.config.AppAuthProperties;
import com.ddmo.app.config.AppRegisterProperties;
import com.ddmo.app.dto.LoginRequest;
import com.ddmo.app.dto.RegisterRequest;
import com.ddmo.app.dto.WxBindRequest;
import com.ddmo.app.dto.WxLoginRequest;
import com.ddmo.app.install.InstallService;
import com.ddmo.app.security.JwtService;
import com.ddmo.app.security.StaffRole;
import com.ddmo.app.security.TenantContext;
import com.ddmo.app.util.SnowflakeIdGenerator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * C 端店长鉴权与本地注册（与 SaaS 开店/邀请码体系隔离）。
 * 多租户云上开店请走 SaaS {@code /api/saas/public/register-shop}。
 */
@Service
public class AuthService {

    private static final String KEY_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";

    private final JdbcTemplate jdbcTemplate;
    private final JwtService jwtService;
    private final SnowflakeIdGenerator idGenerator;
    private final AppAuthProperties authProperties;
    private final AppRegisterProperties registerProperties;
    private final WxMiniProgramService wxMiniProgramService;
    private final TenantAccessService tenantAccessService;
    private final InstallService installService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
        JdbcTemplate jdbcTemplate,
        JwtService jwtService,
        SnowflakeIdGenerator idGenerator,
        AppAuthProperties authProperties,
        AppRegisterProperties registerProperties,
        WxMiniProgramService wxMiniProgramService,
        TenantAccessService tenantAccessService,
        InstallService installService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.jwtService = jwtService;
        this.idGenerator = idGenerator;
        this.authProperties = authProperties;
        this.registerProperties = registerProperties;
        this.wxMiniProgramService = wxMiniProgramService;
        this.tenantAccessService = tenantAccessService;
        this.installService = installService;
    }

    public Map<String, Object> login(LoginRequest request) {
        return login(request, null);
    }

    /**
     * @param clientIp 可选，用于审计明细
     */
    public Map<String, Object> login(LoginRequest request, String clientIp) {
        installService.assertSetupDone();
        String userHint = request != null && request.getUsername() != null
            ? request.getUsername().trim() : "";
        try {
            if (request.getUsername() == null || request.getPassword() == null
                || request.getUsername().isBlank() || request.getPassword().isBlank()) {
                throw new IllegalArgumentException("用户名或密码不能为空");
            }

            String sql = """
                SELECT id, tenant_id, username, password_hash, nickname, status,
                       COALESCE(role, 'owner') AS role
                FROM t_manager WHERE username = ?
                """;
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, request.getUsername().trim());
            if (rows.isEmpty()) {
                throw new IllegalArgumentException("用户名或密码错误");
            }

            Map<String, Object> row = rows.get(0);
            if (!"active".equals(String.valueOf(row.get("status")))) {
                throw new IllegalArgumentException("账号已停用");
            }

            long managerId = ((Number) row.get("id")).longValue();
            String dbHash = String.valueOf(row.get("password_hash"));
            boolean ok = matchesPassword(request.getPassword(), dbHash, managerId);
            if (!ok) {
                throw new IllegalArgumentException("用户名或密码错误");
            }

            long tenantId = ((Number) row.get("tenant_id")).longValue();
            assertTenantActive(tenantId);

            String role = StaffRole.fromCode(String.valueOf(row.get("role"))).code();
            String token = jwtService.generateToken(String.valueOf(row.get("username")), tenantId, managerId, role);
            auditAuth(tenantId, "LOGIN_OK", "用户 " + userHint + " 登录成功"
                + (clientIp != null ? " ip=" + clientIp : ""));
            Map<String, Object> result = new HashMap<>();
            result.put("token", token);
            result.put("user", buildUserMap(row, tenantId));
            return result;
        } catch (IllegalArgumentException ex) {
            auditAuth(0L, "LOGIN_FAIL", "用户 " + userHint + " 登录失败: " + ex.getMessage()
                + (clientIp != null ? " ip=" + clientIp : ""));
            throw ex;
        }
    }

    private void auditAuth(long tenantId, String action, String detail) {
        try {
            long id = idGenerator.nextId();
            String zh = "LOGIN_OK".equals(action) ? "登录成功" : "登录失败";
            jdbcTemplate.update("""
                    INSERT INTO t_audit_log(id, tenant_id, action, entity_type, entity_id, detail, created_at)
                    VALUES (?, ?, ?, 'auth', '', ?, CURRENT_TIMESTAMP)
                    """,
                id, tenantId, zh, detail == null ? "" : detail);
        } catch (Exception ignored) {
            // 审计失败不影响登录主流程
        }
    }

    public Map<String, Object> me() {
        long tenantId = TenantContext.getTenantId();
        Map<String, Object> row = loadCurrentManagerRow(tenantId);
        Map<String, Object> user = buildUserMap(row, tenantId);
        long managerId = ((Number) row.get("id")).longValue();
        user.put("wxBound", isWxBound(managerId));
        return user;
    }

    private Map<String, Object> loadCurrentManagerRow(long tenantId) {
        Long mid = TenantContext.getManagerId();
        String username = TenantContext.getUsername();
        if (mid != null) {
            List<Map<String, Object>> byId = jdbcTemplate.queryForList(
                """
                    SELECT id, username, nickname, status, COALESCE(role, 'owner') AS role
                    FROM t_manager WHERE id = ? AND tenant_id = ?
                    """,
                mid, tenantId
            );
            if (!byId.isEmpty()) {
                return byId.get(0);
            }
        }
        if (username != null && !username.isBlank()) {
            List<Map<String, Object>> byName = jdbcTemplate.queryForList(
                """
                    SELECT id, username, nickname, status, COALESCE(role, 'owner') AS role
                    FROM t_manager WHERE username = ? AND tenant_id = ?
                    """,
                username, tenantId
            );
            if (!byName.isEmpty()) {
                return byName.get(0);
            }
        }
        // 兼容旧 token：无 mid/username 时取店长
        List<Map<String, Object>> owners = jdbcTemplate.queryForList(
            """
                SELECT id, username, nickname, status, COALESCE(role, 'owner') AS role
                FROM t_manager WHERE tenant_id = ?
                ORDER BY CASE COALESCE(role,'owner') WHEN 'owner' THEN 0 ELSE 1 END, id ASC
                LIMIT 1
                """,
            tenantId
        );
        if (owners.isEmpty()) {
            throw new IllegalArgumentException("用户不存在");
        }
        return owners.get(0);
    }

    /**
     * 商家小程序：wx.login code → 已绑定则直接发店长 JWT，否则返回 preToken 引导绑定。
     */
    public Map<String, Object> wxLogin(WxLoginRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        Map<String, String> sess = wxMiniProgramService.code2Session(request.getCode());
        String openid = sess.get("openid");
        List<Map<String, Object>> binds = jdbcTemplate.queryForList(
            """
                SELECT b.manager_id, b.tenant_id, m.username, m.nickname, m.status,
                       COALESCE(m.role, 'owner') AS role
                FROM t_manager_wx_bind b
                JOIN t_manager m ON m.id = b.manager_id
                WHERE b.openid = ?
                """,
            openid
        );
        if (!binds.isEmpty()) {
            Map<String, Object> b = binds.get(0);
            if (!"active".equals(String.valueOf(b.get("status")))) {
                throw new IllegalArgumentException("账号已停用");
            }
            long tenantId = ((Number) b.get("tenant_id")).longValue();
            assertTenantActive(tenantId);
            long managerId = ((Number) b.get("manager_id")).longValue();
            String role = StaffRole.fromCode(String.valueOf(b.get("role"))).code();
            String token = jwtService.generateToken(String.valueOf(b.get("username")), tenantId, managerId, role);
            Map<String, Object> user = buildUserMap(b, tenantId);
            user.put("wxBound", true);
            Map<String, Object> result = new HashMap<>();
            result.put("token", token);
            result.put("user", user);
            result.put("bindRequired", false);
            return result;
        }
        String preToken = jwtService.generateWxPreToken(openid);
        Map<String, Object> result = new HashMap<>();
        result.put("bindRequired", true);
        result.put("preToken", preToken);
        result.put("message", "请使用店长账号密码完成首次绑定");
        return result;
    }

    /**
     * 首次：微信 openid + 店长账号密码绑定；成功后返回 JWT。
     */
    @Transactional
    public Map<String, Object> wxBind(WxBindRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        String openid;
        String unionid = "";
        if (request.getPreToken() != null && !request.getPreToken().isBlank()) {
            openid = jwtService.parseWxPreOpenid(request.getPreToken().trim());
        } else if (request.getCode() != null && !request.getCode().isBlank()) {
            Map<String, String> sess = wxMiniProgramService.code2Session(request.getCode());
            openid = sess.get("openid");
            unionid = sess.getOrDefault("unionid", "");
        } else {
            throw new IllegalArgumentException("请提供 code 或 preToken");
        }
        if (request.getUsername() == null || request.getPassword() == null
            || request.getUsername().isBlank() || request.getPassword().isBlank()) {
            throw new IllegalArgumentException("请输入店长用户名和密码");
        }

        Integer existOpen = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM t_manager_wx_bind WHERE openid = ?", Integer.class, openid
        );
        if (existOpen != null && existOpen > 0) {
            // 已绑定：直接登录
            List<Map<String, Object>> binds = jdbcTemplate.queryForList(
                """
                    SELECT b.manager_id, b.tenant_id, m.username, m.nickname, m.status,
                           COALESCE(m.role, 'owner') AS role
                    FROM t_manager_wx_bind b JOIN t_manager m ON m.id = b.manager_id
                    WHERE b.openid = ?
                    """,
                openid
            );
            if (binds.isEmpty()) {
                throw new IllegalArgumentException("绑定状态异常");
            }
            Map<String, Object> b = binds.get(0);
            long tenantId = ((Number) b.get("tenant_id")).longValue();
            assertTenantActive(tenantId);
            long managerId = ((Number) b.get("manager_id")).longValue();
            String role = StaffRole.fromCode(String.valueOf(b.get("role"))).code();
            String token = jwtService.generateToken(String.valueOf(b.get("username")), tenantId, managerId, role);
            Map<String, Object> user = buildUserMap(b, tenantId);
            user.put("wxBound", true);
            Map<String, Object> result = new HashMap<>();
            result.put("token", token);
            result.put("user", user);
            result.put("bindRequired", false);
            return result;
        }

        LoginRequest loginReq = new LoginRequest();
        loginReq.setUsername(request.getUsername());
        loginReq.setPassword(request.getPassword());
        Map<String, Object> loginResult = login(loginReq);
        // 需要 manager id
        List<Map<String, Object>> mgrs = jdbcTemplate.queryForList(
            """
                SELECT id, tenant_id, username, nickname, status, COALESCE(role, 'owner') AS role
                FROM t_manager WHERE username = ?
                """,
            request.getUsername().trim()
        );
        Map<String, Object> mgr = mgrs.get(0);
        long managerId = ((Number) mgr.get("id")).longValue();
        long tenantId = ((Number) mgr.get("tenant_id")).longValue();

        Integer existMgr = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM t_manager_wx_bind WHERE manager_id = ?", Integer.class, managerId
        );
        if (existMgr != null && existMgr > 0) {
            // 覆盖为新 openid（换手机）
            jdbcTemplate.update(
                "UPDATE t_manager_wx_bind SET openid = ?, unionid = ?, app_id = ?, updated_at = CURRENT_TIMESTAMP WHERE manager_id = ?",
                openid, unionid, wxMiniProgramService.appId(), managerId
            );
        } else {
            jdbcTemplate.update("""
                    INSERT INTO t_manager_wx_bind(id, manager_id, tenant_id, openid, unionid, app_id, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """,
                idGenerator.nextId(), managerId, tenantId, openid, unionid, wxMiniProgramService.appId()
            );
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> user = (Map<String, Object>) loginResult.get("user");
        if (user != null) {
            user.put("wxBound", true);
        }
        loginResult.put("bindRequired", false);
        return loginResult;
    }

    @Transactional
    public void wxUnbind() {
        long tenantId = TenantContext.getTenantId();
        Map<String, Object> row = loadCurrentManagerRow(tenantId);
        long managerId = ((Number) row.get("id")).longValue();
        jdbcTemplate.update("DELETE FROM t_manager_wx_bind WHERE manager_id = ?", managerId);
    }

    public Map<String, Object> wxBindStatus() {
        long tenantId = TenantContext.getTenantId();
        Map<String, Object> m = new HashMap<>();
        try {
            Map<String, Object> row = loadCurrentManagerRow(tenantId);
            long managerId = ((Number) row.get("id")).longValue();
            m.put("bound", isWxBound(managerId));
        } catch (Exception e) {
            m.put("bound", false);
        }
        return m;
    }

    private boolean isWxBound(long managerId) {
        Integer n = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM t_manager_wx_bind WHERE manager_id = ?", Integer.class, managerId
        );
        return n != null && n > 0;
    }

    public Map<String, Object> registerStatus() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM t_manager", Long.class);
        long managers = count == null ? 0 : count;
        String mode = normalizeMode(registerProperties.getMode());
        boolean allowed = switch (mode) {
            case "open" -> true;
            case "invite" -> true;
            case "first-only" -> managers == 0;
            default -> managers == 0;
        };
        Map<String, Object> data = new HashMap<>();
        data.put("mode", mode);
        data.put("allowed", allowed);
        data.put("hasManager", managers > 0);
        data.put("requireInviteCode", "invite".equals(mode));
        data.put("system", "cend");
        return data;
    }

    @Transactional
    public void register(RegisterRequest request) {
        installService.assertSetupDone();
        if (request.getUsername() == null || request.getUsername().isBlank()
            || request.getPassword() == null || request.getPassword().isBlank()
            || request.getNickname() == null || request.getNickname().isBlank()) {
            throw new IllegalArgumentException("用户名、密码、昵称不能为空");
        }

        enforceRegisterPolicy(request);

        String username = request.getUsername().trim();
        String password = request.getPassword().trim();
        String nickname = request.getNickname().trim();
        if (password.length() < 6) {
            throw new IllegalArgumentException("密码长度不能少于6位");
        }
        if (nickname.length() > 6) {
            throw new IllegalArgumentException("昵称最多6个字");
        }
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM t_manager WHERE username = ?",
            Long.class,
            username
        );
        if (count != null && count > 0) {
            throw new IllegalArgumentException("用户名已存在");
        }

        long tenantId = idGenerator.nextId();
        long managerId = idGenerator.nextId();
        String shopName = nickname + "的理发店";
        String tenantKey = generateTenantKey();

        jdbcTemplate.update("""
                INSERT INTO t_tenant(id, tenant_key, shop_name, status, plan_code,
                    max_customers, max_employees, created_at, updated_at)
                VALUES (?, ?, ?, 'active', 'free', 5000, 50, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
            tenantId, tenantKey, shopName);

        jdbcTemplate.update("""
                INSERT INTO t_manager(id, tenant_id, username, password_hash, nickname, role, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'owner', 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
            managerId, tenantId, username, passwordEncoder.encode(password), nickname);

        insertDefaultServiceTypes(tenantId);
    }

    public void changePassword(String oldPassword, String newPassword) {
        if (oldPassword == null || oldPassword.isBlank() || newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("旧密码和新密码不能为空");
        }
        if (newPassword.length() < 6) {
            throw new IllegalArgumentException("新密码长度不能少于6位");
        }
        if (oldPassword.equals(newPassword)) {
            throw new IllegalArgumentException("新密码不能与旧密码相同");
        }

        long tenantId = TenantContext.getTenantId();
        Map<String, Object> current = loadCurrentManagerRow(tenantId);
        long managerId = ((Number) current.get("id")).longValue();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT id, password_hash FROM t_manager WHERE id = ?",
            managerId
        );
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("用户不存在");
        }
        Map<String, Object> row = rows.get(0);
        if (!matchesPassword(oldPassword, String.valueOf(row.get("password_hash")), managerId)) {
            throw new IllegalArgumentException("旧密码错误");
        }
        jdbcTemplate.update(
            "UPDATE t_manager SET password_hash = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
            passwordEncoder.encode(newPassword), managerId
        );
    }

    private void enforceRegisterPolicy(RegisterRequest request) {
        String mode = normalizeMode(registerProperties.getMode());
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM t_manager", Long.class);
        long managers = count == null ? 0 : count;

        switch (mode) {
            case "open" -> {
            }
            case "invite" -> {
                // C 端仅用 application.yml 静态邀请码；库表邀请码归 SaaS
                String expected = registerProperties.getInviteCode() == null
                    ? "" : registerProperties.getInviteCode().trim();
                if (expected.isEmpty()) {
                    throw new IllegalArgumentException("C 端未配置邀请码；云上开店请使用 SaaS 开店入口");
                }
                String provided = request.getInviteCode() == null ? "" : request.getInviteCode().trim();
                if (!expected.equals(provided)) {
                    throw new IllegalArgumentException("邀请码无效");
                }
            }
            case "first-only" -> {
                if (managers > 0) {
                    throw new IllegalArgumentException("已存在店长账号，禁止开放注册（mode=first-only）");
                }
            }
            default -> {
                if (managers > 0) {
                    throw new IllegalArgumentException("当前不允许注册");
                }
            }
        }
    }

    private void assertTenantActive(long tenantId) {
        tenantAccessService.assertCanLogin(tenantId);
    }

    private Map<String, Object> buildUserMap(Map<String, Object> row, long tenantId) {
        Map<String, Object> user = new HashMap<>();
        if (row.get("id") != null) {
            user.put("id", String.valueOf(row.get("id")));
        } else if (row.get("manager_id") != null) {
            user.put("id", String.valueOf(row.get("manager_id")));
        }
        user.put("username", String.valueOf(row.get("username")));
        user.put("nickname", String.valueOf(row.get("nickname")));
        user.put("avatar", "");
        StaffRole role = StaffRole.fromCode(
            row.get("role") == null ? StaffRole.OWNER.code() : String.valueOf(row.get("role"))
        );
        user.put("role", role.code());
        user.put("roleLabel", role.label());
        user.put("permissions", new java.util.ArrayList<>(role.permissions()));
        if (row.containsKey("status")) {
            user.put("status", String.valueOf(row.get("status")));
        }
        user.put("tenantId", String.valueOf(tenantId));
        List<Map<String, Object>> tenants = jdbcTemplate.queryForList(
            "SELECT tenant_key, shop_name, status, plan_code FROM t_tenant WHERE id = ?",
            tenantId
        );
        if (!tenants.isEmpty()) {
            Map<String, Object> t = tenants.get(0);
            user.put("tenantKey", String.valueOf(t.get("tenant_key")));
            user.put("shopName", String.valueOf(t.get("shop_name")));
            user.put("tenantStatus", String.valueOf(t.get("status")));
            user.put("planCode", String.valueOf(t.get("plan_code")));
        }
        return user;
    }

    private String generateTenantKey() {
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder sb = new StringBuilder(8);
            for (int i = 0; i < 8; i++) {
                sb.append(KEY_CHARS.charAt(secureRandom.nextInt(KEY_CHARS.length())));
            }
            String key = sb.toString();
            Integer c = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM t_tenant WHERE tenant_key = ?", Integer.class, key
            );
            if (c == null || c == 0) {
                return key;
            }
        }
        return "t" + Long.toString(idGenerator.nextId(), 36);
    }

    private boolean matchesPassword(String raw, String dbHash, long managerId) {
        if (dbHash.startsWith("$2a$") || dbHash.startsWith("$2b$") || dbHash.startsWith("$2y$")) {
            return passwordEncoder.matches(raw, dbHash);
        }
        if (!authProperties.isAllowPlaintextPassword()) {
            throw new IllegalArgumentException("账号密码格式已过期，请联系管理员重置或开启明文密码兼容迁移");
        }
        boolean ok = raw.equals(dbHash);
        if (ok) {
            jdbcTemplate.update(
                "UPDATE t_manager SET password_hash = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                passwordEncoder.encode(raw),
                managerId
            );
        }
        return ok;
    }

    private static String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return "first-only";
        }
        return mode.trim().toLowerCase(Locale.ROOT);
    }

    private void insertDefaultServiceTypes(long tenantId) {
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM t_service_type WHERE tenant_id = ?",
            Long.class,
            tenantId
        );
        if (count != null && count > 0) {
            return;
        }
        insertService(tenantId, "洗剪吹", BigDecimal.valueOf(58));
        insertService(tenantId, "染发", BigDecimal.valueOf(188));
        insertService(tenantId, "烫发", BigDecimal.valueOf(268));
        insertService(tenantId, "护理", BigDecimal.valueOf(128));
    }

    private void insertService(long tenantId, String name, BigDecimal price) {
        jdbcTemplate.update("""
                INSERT INTO t_service_type(id, tenant_id, name, price, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'active', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
            idGenerator.nextId(), tenantId, name, price);
    }
}
