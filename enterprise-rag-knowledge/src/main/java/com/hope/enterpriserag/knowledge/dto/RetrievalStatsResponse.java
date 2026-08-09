package com.hope.enterpriserag.knowledge.dto;

/** 检索各阶段数量和总耗时统计，不包含查询或文档正文。 */
public record RetrievalStatsResponse(
        int totalRetrieved,
        int permissionFiltered,
        int fusionCandidates,
        int rerankKept,
        long totalTimeMs
) {
}
