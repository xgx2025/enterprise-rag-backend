package com.hope.enterpriserag.chat.dto;

import com.hope.enterpriserag.knowledge.dto.RetrievalStatsResponse;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 对外展示的会话消息，助手消息附带可信状态、引用、检索统计、安全推理摘要和 Trace。
 */
public record ChatMessageResponse(
        String id,
        String role,
        String content,
        List<ChatCitationResponse> citations,
        List<ChatReasoningStepResponse> reasoningSteps,
        String answerStatus,
        RetrievalStatsResponse retrievalStats,
        LocalDateTime timestamp,
        boolean isStreaming,
        String status,
        String traceId,
        String errorCode,
        String errorMessage
) {
}
