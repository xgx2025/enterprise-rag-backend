package com.hope.enterpriserag.knowledge.dto;

import jakarta.validation.constraints.Pattern;

/**
 * 知识库启停请求。
 *
 * @param status 目标状态，仅支持 {@code ACTIVE} 或 {@code DISABLED}
 */
public record KnowledgeBaseStatusRequest(
        @Pattern(regexp = "ACTIVE|DISABLED", message = "知识库状态仅支持 ACTIVE 或 DISABLED") String status
) {
}
