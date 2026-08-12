package com.hope.enterpriserag.chat.dto;

/**
 * 对外展示的推理摘要步骤；内容已经业务层约束，不包含 Prompt 或证据正文。
 */
public record ChatReasoningStepResponse(
        String id,
        String title,
        String detail,
        String status
) {
}
