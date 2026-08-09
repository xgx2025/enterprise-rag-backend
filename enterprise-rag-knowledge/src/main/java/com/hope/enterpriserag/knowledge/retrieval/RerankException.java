package com.hope.enterpriserag.knowledge.retrieval;

/**
 * 重排模型调用或响应校验失败异常。
 * 异常消息不得携带查询、候选正文、API Key 或原始响应体。
 */
public class RerankException extends RuntimeException {
    public RerankException(String message) {
        super(message);
    }

    public RerankException(String message, Throwable cause) {
        super(message, cause);
    }
}
