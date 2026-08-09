package com.hope.enterpriserag.knowledge.dto;

/**
 * 文档分块详情，用于管理端检查父子分块内容及向量化状态。
 *
 * @param id              分块 ID
 * @param documentId      所属文档 ID
 * @param parentChunkId   父块 ID；父块本身为 {@code null}
 * @param chunkIndex      分块在文档内的顺序，从 0 开始
 * @param content         分块文本内容
 * @param sectionPath     分块所属章节路径
 * @param pageNumber      原文件页码；无法识别时为 {@code null}
 * @param tokenCount      估算 Token 数量
 * @param embeddingStatus 向量化状态
 * @param metadataJson    分块类型等扩展元数据 JSON
 */
public record DocumentChunkResponse(
        String id,
        String documentId,
        String parentChunkId,
        int chunkIndex,
        String content,
        String sectionPath,
        Integer pageNumber,
        int tokenCount,
        String embeddingStatus,
        String metadataJson
) {
}
