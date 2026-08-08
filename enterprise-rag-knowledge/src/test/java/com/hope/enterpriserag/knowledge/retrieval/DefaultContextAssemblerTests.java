package com.hope.enterpriserag.knowledge.retrieval;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultContextAssemblerTests {
    private final DefaultContextAssembler assembler = new DefaultContextAssembler();

    @Test
    void deduplicatesParentsAndPreservesDocumentDiversityWithinBudget() {
        RetrievedChunk first = candidate(1L, 1000L, 100L, "差旅制度", "深圳住宿标准为600元", 0.9);
        RetrievedChunk duplicateParent = candidate(2L, 1000L, 100L, "差旅制度", "重复父块", 0.8);
        RetrievedChunk otherDocument = candidate(3L, 2000L, 200L, "报销制度", "报销需要提供支付凭证", 0.7);

        ContextAssembly result = assembler.assemble(
                List.of(first, duplicateParent, otherDocument), 3, 1000, 1);

        assertEquals(2, result.sources().size());
        assertTrue(result.context().contains("【来源 S1】差旅制度"));
        assertTrue(result.context().contains("【来源 S2】报销制度"));
        assertFalse(result.context().contains("重复父块"));
        assertTrue(result.context().length() <= 1000);
    }

    private RetrievedChunk candidate(Long childId, Long parentId, Long documentId, String title,
                                     String parentContent, double rerankScore) {
        return new RetrievedChunk(childId, parentId, documentId, 20L, title, "V1.0", 1, 3,
                LocalDate.of(2026, 1, 1), "正文/第一章", null, childId.intValue(),
                "命中子块", parentContent, 0.8, 0.7, 0.03, rerankScore);
    }
}
