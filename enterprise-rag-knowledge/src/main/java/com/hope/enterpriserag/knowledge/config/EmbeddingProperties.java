package com.hope.enterpriserag.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OpenAI 兼容 Embedding 接口配置。
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
    /** HTTP 连接超时，单位为毫秒。 */
    private int connectTimeoutMillis = 5_000;
    /** 单次 Embedding 请求超时，单位为毫秒。 */
    private int requestTimeoutMillis = 60_000;
    /** 429 或 5xx 等可重试错误的最大尝试次数。 */
    private int maxAttempts = 3;
    /** 相邻重试之间的基础等待时间，单位为毫秒。 */
    private long retryDelayMillis = 500;
}
