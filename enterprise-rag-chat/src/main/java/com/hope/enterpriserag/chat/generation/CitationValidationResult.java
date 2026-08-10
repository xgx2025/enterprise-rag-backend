package com.hope.enterpriserag.chat.generation;

import com.hope.enterpriserag.knowledge.dto.RetrievalSourceResponse;

import java.util.List;

/**
 * 引用白名单校验结果，只返回回答实际引用且来自检索上下文的来源。
 */
public record CitationValidationResult(
        boolean valid,
        List<RetrievalSourceResponse> citedSources,
        String reason
) {
}
