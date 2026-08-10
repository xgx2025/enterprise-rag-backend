package com.hope.enterpriserag.chat.model;

/**
 * 回答模型调用或响应解析失败异常，消息不得携带 Prompt、正文、密钥或原始响应体。
 */
public class ChatModelException extends RuntimeException {
    public ChatModelException(String message) {
        super(message);
    }

    public ChatModelException(String message, Throwable cause) {
        super(message, cause);
    }
}
