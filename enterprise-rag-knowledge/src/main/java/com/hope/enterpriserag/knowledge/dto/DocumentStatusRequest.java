package com.hope.enterpriserag.knowledge.dto;

import jakarta.validation.constraints.Pattern;

/**
 * 文档发布状态变更请求。
 *
 * @param status 目标状态，仅支持 {@code ACTIVE} 或 {@code EXPIRED}
 */
public record DocumentStatusRequest(
        @Pattern(regexp = "ACTIVE|EXPIRED", message = "文档状态仅支持 ACTIVE 或 EXPIRED") String status
) {
}
