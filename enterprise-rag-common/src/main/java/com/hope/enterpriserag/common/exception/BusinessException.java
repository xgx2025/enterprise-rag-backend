package com.hope.enterpriserag.common.exception;

import lombok.Getter;

/**
 * 业务异常，表示正常的业务规则校验不通过。
 * 由 {@code GlobalExceptionHandler} 统一捕获并返回 HTTP 400。
 */
@Getter
public class BusinessException extends RuntimeException {
    /** 异常状态码，默认 400 */
    private final int code;

    public BusinessException(String message) {
        super(message);
        this.code = 400;
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
