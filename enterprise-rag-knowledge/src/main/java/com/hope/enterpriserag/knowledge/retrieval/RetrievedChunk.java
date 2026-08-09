package com.hope.enterpriserag.knowledge.retrieval;

import java.time.LocalDate;

/**
 * 在检索流水线各阶段传递的已授权候选块。
 * 子块用于精确命中，父块正文用于重排和最终上下文组装。
 */
public record RetrievedChunk(
        Long childChunkId,
        Long parentChunkId,
        Long documentId,
        Long knowledgeBaseId,
        String documentTitle,
        String version,
        Integer securityLevel,
        Integer authorityLevel,
        LocalDate effectiveFrom,
        String sectionPath,
        Integer pageNumber,
        Integer chunkIndex,
        String childContent,
        String parentContent,
        double denseScore,
        double sparseScore,
        double fusionScore,
        double rerankScore
) {
    /** 返回补充父块正文后的候选。 */
    public RetrievedChunk withParentContent(String content) {
        return new RetrievedChunk(childChunkId, parentChunkId, documentId, knowledgeBaseId,
                documentTitle, version, securityLevel, authorityLevel, effectiveFrom, sectionPath,
                pageNumber, chunkIndex, childContent, content, denseScore, sparseScore, fusionScore,
                rerankScore);
    }

    /** 返回补充各路召回分数后的候选。 */
    public RetrievedChunk withRetrievalScores(double dense, double sparse, double fusion) {
        return new RetrievedChunk(childChunkId, parentChunkId, documentId, knowledgeBaseId,
                documentTitle, version, securityLevel, authorityLevel, effectiveFrom, sectionPath,
                pageNumber, chunkIndex, childContent, parentContent, dense, sparse, fusion, rerankScore);
    }

    /** 返回补充重排分数后的候选。 */
    public RetrievedChunk withRerankScore(double score) {
        return new RetrievedChunk(childChunkId, parentChunkId, documentId, knowledgeBaseId,
                documentTitle, version, securityLevel, authorityLevel, effectiveFrom, sectionPath,
                pageNumber, chunkIndex, childContent, parentContent, denseScore, sparseScore,
                fusionScore, score);
    }
}
