package com.hope.enterpriserag.knowledge.retrieval;

import com.hope.enterpriserag.knowledge.dto.RetrievalSourceResponse;

import java.util.List;

/**
 * 预算控制后的最终上下文及其来源映射。
 * 来源编号与上下文中的 {@code S1}、{@code S2} 顺序严格一致。
 */
public record ContextAssembly(String context, List<RetrievalSourceResponse> sources) {
}
