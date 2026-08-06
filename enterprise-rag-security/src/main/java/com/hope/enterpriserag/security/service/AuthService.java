package com.hope.enterpriserag.security.service;

import com.hope.enterpriserag.security.dto.LoginRequest;
import com.hope.enterpriserag.security.dto.LoginResponse;
import com.hope.enterpriserag.security.dto.RegisterRequest;
import com.hope.enterpriserag.security.dto.ResetPasswordRequest;

/**
 * 认证服务接口，定义登录、令牌刷新、注册、密码重置和登出操作。
 */
public interface AuthService {

    /** 用户名 + 密码登录，返回 Token 对和用户信息 */
    LoginResponse login(LoginRequest request);

    /** 使用 refresh token 刷新 Token 对（轮转机制，旧 token 一次性使用） */
    LoginResponse refreshToken(String refreshToken);

    /** 用户注册，校验验证码后创建用户并关联默认租户 */
    void register(RegisterRequest request);

    /** 通过邮箱验证码重置密码，同时清除该用户所有 refresh token */
    void resetPassword(ResetPasswordRequest request);

    /** 登出，清除该用户所有 refresh token 白名单 */
    void logout(Long userId);
}
