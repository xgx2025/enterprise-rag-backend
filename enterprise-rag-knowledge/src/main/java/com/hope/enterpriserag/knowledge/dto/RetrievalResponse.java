package com.hope.enterpriserag.knowledge.dto;

import java.util.List;
import java.util.Map;

/**
 * 企业检索完整链路结果，同时兼容前端检索调试页的数据结构。
 * {@code modelOutput} 当前只声明上下文已就绪，不在本服务中伪造模型回答。
 */
public record RetrievalResponse(
        String traceId,
        String query,
        List<ScoredChunkResponse> denseResults,
        List<ScoredChunkResponse> sparseResults,
        List<ScoredChunkResponse> rrfResults,
        List<ScoredChunkResponse> rerankResults,
        String finalContext,
        String modelOutput,
        Map<String, Long> timing,
        List<RetrievalSourceResponse> sources,
        RetrievalStatsResponse retrievalStats
) {
}
