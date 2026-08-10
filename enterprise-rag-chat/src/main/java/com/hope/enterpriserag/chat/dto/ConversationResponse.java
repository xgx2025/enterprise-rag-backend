package com.hope.enterpriserag.chat.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户会话响应；列表接口返回空 messages，详情接口返回完整消息。
 */
public record ConversationResponse(
        String id,
        String title,
        List<String> knowledgeBaseIds,
        String retrievalStrategy,
        List<ChatMessageResponse> messages,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
