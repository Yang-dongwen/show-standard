package com.ddmo.app.controller;

import com.ddmo.app.dto.ApiResponse;
import com.ddmo.app.dto.ChangePasswordRequest;
import com.ddmo.app.dto.LoginRequest;
import com.ddmo.app.dto.RegisterRequest;
import com.ddmo.app.dto.WxBindRequest;
import com.ddmo.app.dto.WxLoginRequest;
import com.ddmo.app.security.LoginRateLimiter;
import com.ddmo.app.service.AuthService;
import com.ddmo.app.util.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final LoginRateLimiter loginRateLimiter;

    public AuthController(AuthService authService, LoginRateLimiter loginRateLimiter) {
        this.authService = authService;
        this.loginRateLimiter = loginRateLimiter;
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(
        @RequestBody LoginRequest request,
        HttpServletRequest httpRequest
    ) {
        String ip = ClientIp.from(httpRequest);
        String user = request != null && request.getUsername() != null ? request.getUsername().trim() : "";
        loginRateLimiter.assertAllowed("ip:" + ip);
        loginRateLimiter.assertAllowed("user:" + user);
        try {
            Map<String, Object> data = authService.login(request, ip);
            loginRateLimiter.clear("ip:" + ip);
            loginRateLimiter.clear("user:" + user);
            return ApiResponse.ok("登录成功", data);
        } catch (IllegalArgumentException ex) {
            loginRateLimiter.recordFailure("ip:" + ip);
            loginRateLimiter.recordFailure("user:" + user);
            throw ex;
        }
    }

    @PostMapping("/register")
    public ApiResponse<Void> register(@RequestBody RegisterRequest request) {
        authService.register(request);
        return ApiResponse.ok("注册成功", null);
    }

    /** 公开：查询当前是否允许注册及模式 */
    @GetMapping("/register-status")
    public ApiResponse<Map<String, Object>> registerStatus() {
        return ApiResponse.ok(authService.registerStatus());
    }

    /** 商家小程序：wx.login code 直登或返回 bindRequired */
    @PostMapping("/wx-login")
    public ApiResponse<Map<String, Object>> wxLogin(
        @RequestBody WxLoginRequest request,
        HttpServletRequest httpRequest
    ) {
        String ip = ClientIp.from(httpRequest);
        loginRateLimiter.assertAllowed("wx-ip:" + ip);
        try {
            Map<String, Object> data = authService.wxLogin(request);
            boolean bind = Boolean.TRUE.equals(data.get("bindRequired"));
            if (!bind) {
                loginRateLimiter.clear("wx-ip:" + ip);
            }
            return ApiResponse.ok(bind ? "需要绑定店长账号" : "登录成功", data);
        } catch (IllegalArgumentException ex) {
            loginRateLimiter.recordFailure("wx-ip:" + ip);
            throw ex;
        }
    }

    /** 商家小程序：openid + 账号密码首次绑定 */
    @PostMapping("/wx-bind")
    public ApiResponse<Map<String, Object>> wxBind(
        @RequestBody WxBindRequest request,
        HttpServletRequest httpRequest
    ) {
        String ip = ClientIp.from(httpRequest);
        loginRateLimiter.assertAllowed("wx-ip:" + ip);
        try {
            Map<String, Object> data = authService.wxBind(request);
            loginRateLimiter.clear("wx-ip:" + ip);
            return ApiResponse.ok("绑定成功", data);
        } catch (IllegalArgumentException ex) {
            loginRateLimiter.recordFailure("wx-ip:" + ip);
            throw ex;
        }
    }

    @GetMapping("/wx-bind-status")
    public ApiResponse<Map<String, Object>> wxBindStatus() {
        return ApiResponse.ok(authService.wxBindStatus());
    }

    @PostMapping("/wx-unbind")
    public ApiResponse<Void> wxUnbind() {
        authService.wxUnbind();
        return ApiResponse.ok("已解除微信绑定", null);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        return ApiResponse.ok("已退出登录", null);
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me() {
        return ApiResponse.ok(authService.me());
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@RequestBody ChangePasswordRequest request) {
        authService.changePassword(request.getOldPassword(), request.getNewPassword());
        return ApiResponse.ok("密码修改成功", null);
    }
}
