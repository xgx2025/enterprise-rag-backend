package com.hope.enterpriserag.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Milvus 向量数据库配置，对应 {@code rag.milvus} 前缀。
 * Collection 由应用首次写入时按预期维度和治理字段自动创建。
 */
@Data
@ConfigurationProperties(prefix = "rag.milvus")
public class MilvusProperties {
    /** Milvus gRPC/HTTP 接入地址，例如 {@code http://localhost:19530}。 */
    private String uri = "http://localhost:19530";
    /** Milvus Token，格式通常为 {@code username:password}；无鉴权时可留空。 */
    private String token;
    /** Milvus 数据库名称。 */
    private String databaseName = "default";
    /** 保存企业文档子块向量的 Collection 名称。 */
    private String collectionName = "enterprise_rag_chunks";
}
