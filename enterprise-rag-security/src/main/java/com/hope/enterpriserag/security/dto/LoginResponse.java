package com.hope.enterpriserag.security.dto;

import lombok.Data;

/**
 * 登录 / 刷新令牌响应，包含 Token 对和用户基本信息。
 * <p>
 * refreshToken 在 Controller 层写入 HttpOnly Cookie 后会置空，
 * 前端仅收到 accessToken、expiresIn 和 userInfo。
 */
@Data
public class LoginResponse {
    /** JWT 访问令牌 */
    private String accessToken;
    /** JWT 刷新令牌（会通过 HttpOnly Cookie 下发） */
    private String refreshToken;
    /** 访问令牌有效期，单位毫秒 */
    private long expiresIn;
    /** 当前登录用户的基本信息 */
    private UserInfo userInfo;

    /**
     * 用户基本信息。
     */
    @Data
    public static class UserInfo {
        /** 用户 ID */
        private Long userId;
        /** 所属租户 ID */
        private Long tenantId;
        /** 登录用户名 */
        private String username;
        /** 真实姓名 */
        private String realName;
    }
}
