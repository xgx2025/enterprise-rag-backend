package com.hope.enterpriserag.server.controller;

import com.hope.enterpriserag.common.Result;
import com.hope.enterpriserag.security.dto.LoginRequest;
import com.hope.enterpriserag.security.dto.LoginResponse;
import com.hope.enterpriserag.security.dto.RegisterRequest;
import com.hope.enterpriserag.security.dto.ResetPasswordRequest;
import com.hope.enterpriserag.server.dto.SendCodeRequest;
import com.hope.enterpriserag.system.entity.User;
import com.hope.enterpriserag.security.service.AuthService;
import com.hope.enterpriserag.security.service.EmailService;
import com.hope.enterpriserag.security.exception.AuthException;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

/**
 * 认证控制器，提供注册、登录、令牌刷新、登出、密码重置等 REST 接口。
 * <p>
 * Refresh Token 通过 HttpOnly Cookie 下发，前端 JS 不可读取，降低 XSS 泄露风险。
 * Access Token 在响应 body 中返回，前端存储于内存并在每次请求时通过 Authorization 头传递。
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String REFRESH_TOKEN_COOKIE = "refresh_token";
    private static final Duration REFRESH_COOKIE_MAX_AGE = Duration.ofDays(7);

    private final AuthService authService;
    private final EmailService emailService;

    /** 用户名 + 密码登录 */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                        HttpServletResponse response) {
        log.info("用户登录请求: username={}", request.getUsername());
        LoginResponse loginResponse = authService.login(request);
        setRefreshTokenCookie(response, loginResponse.getRefreshToken());
        loginResponse.setRefreshToken(null);
        log.info("用户登录成功: userId={}", loginResponse.getUserInfo().getUserId());
        return Result.ok(loginResponse);
    }

    /** 使用 Refresh Token（Cookie）刷新 Access Token */
    @PostMapping("/refresh")
    public Result<LoginResponse> refresh(@CookieValue(value = REFRESH_TOKEN_COOKIE, required = false) String refreshToken,
                                      HttpServletResponse response) {
        if (refreshToken == null || refreshToken.isBlank()) {
            log.warn("刷新令牌请求缺少cookie");
            throw new AuthException("刷新令牌不存在");
        }
        log.debug("刷新令牌请求");
        LoginResponse loginResponse = authService.refreshToken(refreshToken);
        setRefreshTokenCookie(response, loginResponse.getRefreshToken());
        loginResponse.setRefreshToken(null);
        return Result.ok(loginResponse);
    }

    /** 登出 */
    @PostMapping("/logout")
    public Result<Void> logout(@AuthenticationPrincipal User user,
                                HttpServletResponse response) {
        if (user != null) {
            log.info("用户登出: userId={}", user.getId());
            authService.logout(user.getId());
        }
        clearRefreshTokenCookie(response);
        return Result.ok();
    }

    /** 用户注册 */
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterRequest request) {
        log.info("用户注册请求: username={}, email={}", request.getUsername(), request.getEmail());
        authService.register(request);
        log.info("用户注册成功: username={}", request.getUsername());
        return Result.ok();
    }

    /** 通过邮箱验证码重置密码 */
    @PostMapping("/reset-password")
    public Result<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        log.info("密码重置请求: email={}", request.getEmail());
        authService.resetPassword(request);
        log.info("密码重置成功: email={}", request.getEmail());
        return Result.ok();
    }

    /** 发送邮箱验证码 */
    @PostMapping("/send-code")
    public Result<Void> sendCode(@Valid @RequestBody SendCodeRequest request) {
        log.info("验证码发送请求: email={}", request.getEmail());
        emailService.sendVerificationCode(request.getEmail());
        return Result.ok();
    }

    /** 获取当前登录用户的基本信息 */
    @GetMapping("/profile")
    public Result<LoginResponse.UserInfo> me(@AuthenticationPrincipal User user) {
        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo();
        userInfo.setUserId(user.getId());
        userInfo.setTenantId(user.getTenantId());
        userInfo.setUsername(user.getUsername());
        userInfo.setRealName(user.getRealName());
        return Result.ok(userInfo);
    }

    /** 将 Refresh Token 写入 HttpOnly Cookie */
    private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, refreshToken)
            .httpOnly(true)
            .secure(false)
            .path("/")
            .maxAge(REFRESH_COOKIE_MAX_AGE)
            .sameSite("Lax")
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /** 清除 Refresh Token Cookie */
    private void clearRefreshTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, "")
            .httpOnly(true)
            .secure(false)
            .path("/")
            .maxAge(0)
            .sameSite("Lax")
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
