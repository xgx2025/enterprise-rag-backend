package com.hope.enterpriserag.knowledge.vector;

import java.time.LocalDate;

/**
 * 文档生命周期变化时需要同步到 Milvus 的可过滤元数据。
 * 通过部分 Upsert 更新，不重新调用 Embedding 模型。
 */
public record VectorMetadata(
        Long chunkId,
        String documentStatus,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
) {
}
