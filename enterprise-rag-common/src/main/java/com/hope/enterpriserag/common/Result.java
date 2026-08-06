package com.hope.enterpriserag.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一响应体，所有 Controller 接口通过此类返回标准化的 JSON 结构。
 *
 * @param <T> 业务数据的类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {
    /** 业务状态码，200 表示成功 */
    private int code;
    /** 提示信息 */
    private String message;
    /** 响应数据，可为 null */
    private T data;

    /** 返回成功响应（带数据） */
    public static <T> Result<T> ok(T data) {
        return new Result<>(200, "success", data);
    }

    /** 返回成功响应（无数据） */
    public static <T> Result<T> ok() {
        return ok(null);
    }

    /** 返回失败响应（自定义状态码和消息） */
    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

    /** 返回失败响应（默认 500 状态码） */
    public static <T> Result<T> fail(String message) {
        return fail(500, message);
    }
}
