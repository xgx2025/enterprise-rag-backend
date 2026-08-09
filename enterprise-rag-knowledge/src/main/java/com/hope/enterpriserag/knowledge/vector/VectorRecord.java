package com.hope.enterpriserag.knowledge.vector;

import java.time.LocalDate;
import java.util.List;

/**
 * 写入向量数据库的子块记录。
 * MySQL 中的子块正文是主数据；{@code content} 仅作为 Milvus BM25 分词和混合检索所需的冗余副本。
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
        String content,
        List<Float> embedding
) {
}
