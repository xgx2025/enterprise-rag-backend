package com.hope.enterpriserag.knowledge.dto;

import java.time.LocalDateTime;

/**
 * 知识库管理接口返回对象。
 *
 * @param id            知识库 ID
 * @param name          知识库名称
 * @param documentCount 未归档文档数量
 * @param description   知识库用途说明
 * @param department    归属部门
 * @param securityLevel 默认安全等级，范围为 1 至 3
 * @param status        状态：{@code ACTIVE} 或 {@code DISABLED}
 * @param createdAt     创建时间
 * @param updatedAt     最后更新时间
 */
public record KnowledgeBaseResponse(
        String id,
        String name,
        long documentCount,
        String description,
        String department,
        int securityLevel,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
