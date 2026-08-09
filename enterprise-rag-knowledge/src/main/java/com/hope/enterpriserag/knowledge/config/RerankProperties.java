package com.hope.enterpriserag.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 外部重排模型配置。
 * 模型调用默认关闭；API Key 只能通过环境变量或部署平台 Secret 注入，不得写入源码和日志。
 */
@Data
@ConfigurationProperties(prefix = "rag.rerank")
public class RerankProperties {
    /** 是否使用外部 Qwen3-VL-Rerank；关闭时使用本地启发式重排。 */
    private boolean modelEnabled;
    /** 百炼完整请求地址，必须包含业务空间 ID 和地域域名。 */
    private String endpoint;
    /** 百炼 API Key。 */
    private String apiKey;
    /** 百炼模型 ID；当前协议仅支持 {@code qwen3-vl-rerank}。 */
    private String model = "qwen3-vl-rerank";
    /** 英文重排指令；默认按问答检索相关度排序。 */
    private String instruct = "Given a web search query, retrieve relevant passages that answer the query.";
    /** 外部模型失败时是否降级到本地启发式重排。 */
    private boolean fallbackEnabled = true;
    /** HTTP 连接超时，单位为毫秒。 */
    private int connectTimeoutMillis = 5_000;
    /** 单次重排请求超时，单位为毫秒。 */
    private int requestTimeoutMillis = 60_000;
    /** 408、429、5xx 或网络异常的最大尝试次数。 */
    private int maxAttempts = 3;
    /** 相邻重试之间的指数退避基础等待时间，单位为毫秒。 */
    private long retryDelayMillis = 500;
}
