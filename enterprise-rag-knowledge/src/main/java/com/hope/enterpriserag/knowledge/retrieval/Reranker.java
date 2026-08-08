package com.hope.enterpriserag.knowledge.retrieval;

import java.util.List;

/**
 * 检索候选重排契约。
 * 实现可以使用本地确定性算法、交叉编码器或外部 Rerank 服务，但不得扩大候选权限范围。
 */
public interface Reranker {
    /** 按与查询的最终相关度从高到低返回候选。 */
    List<RetrievedChunk> rerank(String query, List<RetrievedChunk> candidates);
}
