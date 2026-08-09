package com.hope.enterpriserag.knowledge.retrieval;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * 无外部模型依赖的第一版重排器。
 * 综合父块词项覆盖、稠密相似度、RRF 分数和文档权威等级，后续可替换为交叉编码器。
 */
@Component
public class HeuristicReranker implements Reranker {
    @Override
    public List<RetrievedChunk> rerank(String query, List<RetrievedChunk> candidates) {
        double maximumFusion = candidates.stream().mapToDouble(RetrievedChunk::fusionScore).max().orElse(1.0);
        return candidates.stream()
                .map(candidate -> candidate.withRerankScore(score(query, candidate, maximumFusion)))
                .sorted(Comparator.comparingDouble(RetrievedChunk::rerankScore).reversed()
                        .thenComparing(RetrievedChunk::childChunkId))
                .toList();
    }

    private double score(String query, RetrievedChunk candidate, double maximumFusion) {
        String content = candidate.parentContent() == null ? candidate.childContent() : candidate.parentContent();
        double lexical = RetrievalTextAnalyzer.lexicalScore(query, content);
        double dense = Math.max(0.0, Math.min(1.0, (candidate.denseScore() + 1.0) / 2.0));
        double fusion = maximumFusion <= 0 ? 0.0 : candidate.fusionScore() / maximumFusion;
        double authority = candidate.authorityLevel() == null ? 0.0
                : Math.max(0.0, Math.min(1.0, candidate.authorityLevel() / 3.0));
        return lexical * 0.50 + dense * 0.25 + fusion * 0.15 + authority * 0.10;
    }
}
