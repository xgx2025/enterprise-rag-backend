package com.hope.enterpriserag.chat.model;

import com.hope.enterpriserag.chat.config.ChatModelProperties;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.time.Duration;
import java.util.List;

/**
 * 基于 Spring AI OpenAI 模型实现的回答模型适配器。
 * 业务层继续依赖项目自己的 {@link ChatModel}，供应商协议、重试和观测能力由 Spring AI 负责。
 */
@Slf4j
public class SpringAiChatModel implements ChatModel {
    private static final String COMPLETIONS_PATH = "/chat/completions";

    private final ChatModelProperties properties;
    private final org.springframework.ai.chat.model.ChatModel delegate;

    public SpringAiChatModel(ChatModelProperties properties) {
        this(properties, ObservationRegistry.NOOP);
    }

    public SpringAiChatModel(ChatModelProperties properties, ObservationRegistry observationRegistry) {
        this.properties = properties;
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .baseUrl(baseUrl(properties.getEndpoint(), COMPLETIONS_PATH))
                .apiKey(properties.getApiKey().trim())
                .model(properties.getModel().trim())
                .temperature(properties.getTemperature())
                .maxTokens(properties.getMaxTokens())
                .timeout(Duration.ofMillis(properties.getRequestTimeoutMillis()))
                .maxRetries(Math.max(0, properties.getMaxAttempts() - 1))
                .build();
        this.delegate = OpenAiChatModel.builder()
                .options(options)
                .observationRegistry(observationRegistry)
                .build();
    }

    SpringAiChatModel(ChatModelProperties properties,
                      org.springframework.ai.chat.model.ChatModel delegate) {
        this.properties = properties;
        this.delegate = delegate;
    }

    @Override
    public ChatModelResult generate(ChatModelPrompt prompt) {
        if (prompt == null || prompt.systemPrompt() == null || prompt.userPrompt() == null) {
            throw new ChatModelException("Chat 模型 Prompt 不能为空");
        }
        try {
            ChatResponse response = delegate.call(new Prompt(List.of(
                    new SystemMessage(prompt.systemPrompt()),
                    new UserMessage(prompt.userPrompt()))));
            Generation generation = response == null ? null : response.getResult();
            String content = generation == null || generation.getOutput() == null
                    ? null : generation.getOutput().getText();
            if (content == null || content.isBlank()) {
                throw new ChatModelException("Chat 模型未返回回答内容");
            }
            Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
            return new ChatModelResult(content, token(usage == null ? null : usage.getPromptTokens()),
                    token(usage == null ? null : usage.getCompletionTokens()),
                    token(usage == null ? null : usage.getTotalTokens()));
        } catch (ChatModelException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("Spring AI Chat 模型调用失败: model={}", modelName(), e);
            throw new ChatModelException("Chat 模型调用失败", e);
        }
    }

    @Override
    public String modelName() {
        return properties.getModel().trim();
    }

    static String baseUrl(String endpoint, String resourcePath) {
        String normalized = endpoint.trim().replaceAll("/+$", "");
        if (!normalized.endsWith(resourcePath)) {
            throw new IllegalArgumentException("模型 endpoint 必须以 " + resourcePath + " 结尾");
        }
        return normalized.substring(0, normalized.length() - resourcePath.length());
    }

    private int token(Integer value) {
        return value == null ? 0 : value;
    }
}
