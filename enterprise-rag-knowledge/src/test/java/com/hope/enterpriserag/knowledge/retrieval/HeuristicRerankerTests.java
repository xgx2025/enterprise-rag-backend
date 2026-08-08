package com.hope.enterpriserag.knowledge.retrieval;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeuristicRerankerTests {
    private final HeuristicReranker reranker = new HeuristicReranker();

    @Test
    void promotesParentWithExactBusinessTerms() {
        RetrievedChunk unrelated = candidate(1L, "员工请假流程", 0.9);
        RetrievedChunk exact = candidate(2L, "深圳出差住宿标准为每人每天600元", 0.6);

        List<RetrievedChunk> result = reranker.rerank("深圳住宿标准", List.of(unrelated, exact));

        assertEquals(2L, result.getFirst().childChunkId());
        assertTrue(result.getFirst().rerankScore() > result.getLast().rerankScore());
    }

    private RetrievedChunk candidate(Long id, String parentContent, double denseScore) {
        return new RetrievedChunk(id, id + 1000, 100L, 20L, "差旅制度", "V1.0", 1, 2,
                LocalDate.of(2026, 1, 1), "正文", null, id.intValue(), "子块", parentContent,
                denseScore, 0.0, 0.02, 0.0);
    }
}
