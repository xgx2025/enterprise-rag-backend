package com.hope.enterpriserag.knowledge.vector;

import java.util.List;

/**
 * 文档子块向量存储契约，隔离业务流水线与具体向量数据库 SDK。
 */
public interface VectorStore {

    /** 确保目标集合及过滤索引已存在。 */
    void ensureReady(int dimensions);

    /** 删除指定租户文档的旧向量，供重新解析和幂等重试使用。 */
    void deleteDocument(Long tenantId, Long documentId);

    /** 按子块主键幂等写入一批完整向量记录。 */
    void upsert(List<VectorRecord> records);

    /** 更新已存在向量的文档生命周期元数据，不重新生成向量。 */
    void updateMetadata(List<VectorMetadata> metadata);

    /**
     * 在服务端生成的租户、知识库、状态、安全等级和有效期过滤范围内执行稠密向量检索。
     * 返回值只包含定位元数据，不返回文档正文。
     */
    List<VectorSearchHit> search(VectorSearchRequest request);
}
