package com.hope.enterpriserag.security.dto;

import lombok.Data;

/**
 * 刷新令牌请求参数（备用，当前使用 HttpOnly Cookie 方式）。
 */
@Data
public class RefreshRequest {
    private String refreshToken;
}
