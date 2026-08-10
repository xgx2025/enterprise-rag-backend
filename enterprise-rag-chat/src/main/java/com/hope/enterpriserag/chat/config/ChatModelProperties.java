package com.hope.enterpriserag.chat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Spring AI OpenAI Chat Completions 兼容模型配置。
 * API Key 必须通过环境变量或部署平台 Secret 注入，不得写入源码和日志。
 */
@Data
@ConfigurationProperties(prefix = "rag.chat.model")
public class ChatModelProperties {
    /** 完整的 {@code /chat/completions} 请求地址。 */
    private String endpoint;
    /** 模型服务 API Key。 */
    private String apiKey;
    /** Chat 模型 ID。 */
    private String model;
    /** 生成温度；可信问答应保持较低值。 */
    private double temperature = 0.1;
    /** 单次回答最大输出 Token。 */
    private int maxTokens = 1_500;
    /** Spring AI 单次生成请求总超时，单位为毫秒。 */
    private int requestTimeoutMillis = 120_000;
    /** 包含首次请求在内的最大尝试次数，传给 Spring AI 时换算为最大重试次数。 */
    private int maxAttempts = 2;
}
