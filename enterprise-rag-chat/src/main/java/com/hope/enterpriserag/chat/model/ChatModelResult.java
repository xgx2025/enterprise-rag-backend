package com.hope.enterpriserag.chat.model;

/**
 * 模型完整生成结果及 Token 用量；不保存原始供应商响应。
 */
public record ChatModelResult(
        String content,
        int promptTokens,
        int completionTokens,
        int totalTokens
) {
}
