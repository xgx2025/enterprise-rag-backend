package com.hope.enterpriserag.chat.config;

import com.hope.enterpriserag.chat.model.ChatModel;
import com.hope.enterpriserag.chat.model.OpenAiCompatibleChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.net.URI;

/**
 * 可信问答基础设施装配，仅在显式启用 Chat 时创建外部模型客户端。
 */
@Configuration
@ConditionalOnProperty(prefix = "rag.chat", name = "enabled", havingValue = "true")
@EnableConfigurationProperties({ChatProperties.class, ChatModelProperties.class})
public class ChatConfiguration {
    /** 创建 OpenAI Chat Completions 协议兼容的模型客户端。 */
    @Bean
    public ChatModel chatModel(ChatModelProperties properties) {
        validate(properties);
        return new OpenAiCompatibleChatModel(properties);
    }

    private void validate(ChatModelProperties properties) {
        if (!StringUtils.hasText(properties.getEndpoint())) {
            throw new IllegalStateException("rag.chat.model.endpoint 不能为空");
        }
        URI endpoint;
        try {
            endpoint = URI.create(properties.getEndpoint().trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("rag.chat.model.endpoint 格式无效", e);
        }
        if (!endpoint.isAbsolute() || !("https".equalsIgnoreCase(endpoint.getScheme())
                || "http".equalsIgnoreCase(endpoint.getScheme()))) {
            throw new IllegalStateException("rag.chat.model.endpoint 必须是完整的 HTTP(S) 地址");
        }
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new IllegalStateException("rag.chat.model.api-key 不能为空");
        }
        if (!StringUtils.hasText(properties.getModel())) {
            throw new IllegalStateException("rag.chat.model.model 不能为空");
        }
        if (properties.getTemperature() < 0 || properties.getTemperature() > 2) {
            throw new IllegalStateException("rag.chat.model.temperature 必须在 0 到 2 之间");
        }
        if (properties.getMaxTokens() <= 0 || properties.getConnectTimeoutMillis() <= 0
                || properties.getRequestTimeoutMillis() <= 0) {
            throw new IllegalStateException("Chat 模型 Token 或超时配置无效");
        }
        if (properties.getMaxAttempts() <= 0 || properties.getRetryDelayMillis() < 0) {
            throw new IllegalStateException("Chat 模型重试配置无效");
        }
    }
}
