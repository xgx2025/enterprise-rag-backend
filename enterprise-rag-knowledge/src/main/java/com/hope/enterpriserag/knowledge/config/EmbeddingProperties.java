package com.hope.enterpriserag.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Spring AI OpenAI 兼容 Embedding 接口配置。
 * API Key 只能通过环境变量注入，禁止写入源码、配置样例或日志。
 */
@Data
@ConfigurationProperties(prefix = "rag.embedding")
public class EmbeddingProperties {
    /** 完整的 Embedding 请求地址，例如 {@code http://localhost:11434/v1/embeddings}。 */
    private String endpoint;
    /** OpenAI 兼容接口密钥；本地免鉴权服务可留空。 */
    private String apiKey;
    /** Embedding 模型名称。 */
    private String model;
    /** 模型输出向量维度，必须与 Milvus Collection 一致。 */
    private int dimensions = 1024;
    /** 是否在请求体中发送 dimensions 参数，以兼容不支持该参数的服务。 */
    private boolean sendDimensions;
    /** Spring AI 单次 Embedding 请求总超时，单位为毫秒。 */
    private int requestTimeoutMillis = 60_000;
    /** 包含首次请求在内的最大尝试次数，传给 Spring AI 时换算为最大重试次数。 */
    private int maxAttempts = 3;
}
