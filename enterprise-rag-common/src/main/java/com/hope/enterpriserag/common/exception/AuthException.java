package com.hope.enterpriserag.common.exception;

import lombok.Getter;

/**
 * 认证异常，表示用户未登录或凭证无效。
 * 由 {@code GlobalExceptionHandler} 统一捕获并返回 HTTP 401。
 */
@Getter
public class AuthException extends RuntimeException {
    /** 异常状态码，默认 401 */
    private final int code;

    public AuthException(String message) {
        super(message);
        this.code = 401;
    }

    public AuthException(int code, String message) {
        super(message);
        this.code = code;
    }
}
