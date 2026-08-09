package com.hope.enterpriserag.knowledge.vector;

import java.util.List;

/**
 * Milvus 混合检索各阶段的定位结果。
 * 三组结果均不包含正文，调用方必须回到 MySQL 做归属校验并加载权威内容。
 *
 * @param denseHits  稠密向量召回结果
 * @param sparseHits BM25 召回结果
 * @param hybridHits Milvus RRF 融合结果；只启用一路时等于该路结果
 */
public record VectorSearchResult(
        List<VectorSearchHit> denseHits,
        List<VectorSearchHit> sparseHits,
        List<VectorSearchHit> hybridHits
) {
    public VectorSearchResult {
        denseHits = List.copyOf(denseHits);
        sparseHits = List.copyOf(sparseHits);
        hybridHits = List.copyOf(hybridHits);
    }
}
