package com.hope.enterpriserag.knowledge.embedding;

/**
 * Embedding 服务调用异常。
 * 异常消息只携带状态和协议错误，不包含请求正文、密钥或外部响应正文。
 */
public class EmbeddingException extends RuntimeException {
    public EmbeddingException(String message) {
        super(message);
    }

    public EmbeddingException(String message, Throwable cause) {
        super(message, cause);
    }
}
