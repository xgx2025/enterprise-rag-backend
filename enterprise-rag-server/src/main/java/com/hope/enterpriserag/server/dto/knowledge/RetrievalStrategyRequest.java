package com.hope.enterpriserag.server.dto.knowledge;

/**
 * 检索调试策略开关。
 * 未提供的字段默认启用；RRF 在 Dense 和 Sparse 均开启时自动融合两路结果。
 */
public record RetrievalStrategyRequest(Boolean dense, Boolean sparse, Boolean rerank) {
    public boolean denseEnabled() {
        return dense == null || dense;
    }

    public boolean sparseEnabled() {
        return sparse == null || sparse;
    }

    public boolean rerankEnabled() {
        return rerank == null || rerank;
    }
}
