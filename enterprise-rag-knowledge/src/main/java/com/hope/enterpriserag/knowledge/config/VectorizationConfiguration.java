package com.hope.enterpriserag.knowledge.config;

import com.hope.enterpriserag.knowledge.embedding.EmbeddingService;
import com.hope.enterpriserag.knowledge.embedding.OpenAiCompatibleEmbeddingService;
import com.hope.enterpriserag.knowledge.vector.MilvusVectorStore;
import com.hope.enterpriserag.knowledge.vector.VectorStore;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * 文档向量化基础设施配置。
 * 只有显式启用流水线时才创建外部服务客户端，避免本地无 Milvus 环境时影响应用启动。
 */
@Configuration
@ConditionalOnProperty(prefix = "rag.vectorization", name = "enabled", havingValue = "true")
@EnableConfigurationProperties({
        VectorizationProperties.class,
        EmbeddingProperties.class,
        MilvusProperties.class,
        RetrievalProperties.class
})
public class VectorizationConfiguration {

    /** 创建 OpenAI 协议兼容的 Embedding 服务适配器。 */
    @Bean
    public EmbeddingService embeddingService(EmbeddingProperties properties) {
        validateEmbedding(properties);
        return new OpenAiCompatibleEmbeddingService(properties);
    }

    /** 创建 Milvus Java SDK 客户端；容器关闭时同步释放连接。 */
    @Bean(destroyMethod = "close")
    public MilvusClientV2 milvusClient(MilvusProperties properties) {
        if (!StringUtils.hasText(properties.getUri())) {
            throw new IllegalStateException("rag.milvus.uri 不能为空");
        }
        ConnectConfig connectConfig;
        if (StringUtils.hasText(properties.getToken())) {
            connectConfig = ConnectConfig.builder()
                    .uri(properties.getUri().trim())
                    .token(properties.getToken().trim())
                    .build();
        } else {
            connectConfig = ConnectConfig.builder()
                    .uri(properties.getUri().trim())
                    .build();
        }
        return new MilvusClientV2(connectConfig);
    }

    /** 创建 Milvus 向量存储适配器。 */
    @Bean
    public VectorStore vectorStore(MilvusClientV2 client, MilvusProperties properties) {
        if (!StringUtils.hasText(properties.getCollectionName())) {
            throw new IllegalStateException("rag.milvus.collection-name 不能为空");
        }
        return new MilvusVectorStore(client, properties);
    }

    private void validateEmbedding(EmbeddingProperties properties) {
        if (!StringUtils.hasText(properties.getEndpoint())) {
            throw new IllegalStateException("rag.embedding.endpoint 不能为空");
        }
        if (!StringUtils.hasText(properties.getModel())) {
            throw new IllegalStateException("rag.embedding.model 不能为空");
        }
        if (properties.getDimensions() <= 1) {
            throw new IllegalStateException("rag.embedding.dimensions 必须大于 1");
        }
        if (properties.getConnectTimeoutMillis() <= 0 || properties.getRequestTimeoutMillis() <= 0) {
            throw new IllegalStateException("Embedding HTTP 超时时间必须大于 0");
        }
        if (properties.getMaxAttempts() <= 0 || properties.getRetryDelayMillis() < 0) {
            throw new IllegalStateException("Embedding 重试配置无效");
        }
    }
}
