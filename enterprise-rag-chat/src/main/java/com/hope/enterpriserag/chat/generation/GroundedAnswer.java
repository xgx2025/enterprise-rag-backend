package com.hope.enterpriserag.chat.generation;

import com.hope.enterpriserag.chat.model.ChatModelResult;
import com.hope.enterpriserag.knowledge.dto.RetrievalResponse;
import com.hope.enterpriserag.knowledge.dto.RetrievalSourceResponse;

import java.util.List;

/**
 * 经过证据门控和引用白名单校验的完整回答。
 */
public record GroundedAnswer(
        String content,
        AnswerStatus status,
        List<RetrievalSourceResponse> citations,
        RetrievalResponse retrieval,
        ChatModelResult modelResult,
        String modelName,
        List<ReasoningStep> reasoningSteps
) {
    public GroundedAnswer {
        citations = citations == null ? List.of() : List.copyOf(citations);
        reasoningSteps = reasoningSteps == null ? List.of() : List.copyOf(reasoningSteps);
    }
}
