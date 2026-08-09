package com.hope.enterpriserag.knowledge.vector;

/**
 * 向量数据库返回的子块定位信息。
 * 不携带正文，正文只能在通过 MySQL 权限复核后按块 ID 加载。
 *
 * @param chunkId       子块 ID
 * @param documentId    文档 ID
 * @param parentChunkId 父块 ID
 * @param chunkIndex    子块在文档中的顺序
 * @param score         Milvus 相似度分数
 */
public record VectorSearchHit(
        Long chunkId,
        Long documentId,
        Long parentChunkId,
        Integer chunkIndex,
        double score
) {
}
