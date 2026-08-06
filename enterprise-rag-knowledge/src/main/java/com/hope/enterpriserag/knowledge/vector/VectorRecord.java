package com.hope.enterpriserag.knowledge.vector;

import java.time.LocalDate;
import java.util.List;

/**
 * 写入向量数据库的子块记录。
 * 仅包含检索过滤和定位所需元数据，不携带文档正文。
 */
public record VectorRecord(
        Long chunkId,
        Long tenantId,
        Long knowledgeBaseId,
        Long documentId,
        Long parentChunkId,
        Integer chunkIndex,
        String version,
        String documentStatus,
        String department,
        Integer securityLevel,
        Integer authorityLevel,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String allowedRoles,
        String sectionPath,
        Integer pageNumber,
        List<Float> embedding
) {
}
