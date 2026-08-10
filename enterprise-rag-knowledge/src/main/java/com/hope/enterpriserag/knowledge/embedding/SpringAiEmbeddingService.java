package com.hope.enterpriserag.knowledge.embedding;

import com.hope.enterpriserag.knowledge.config.EmbeddingProperties;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 基于 Spring AI OpenAI 模型实现的文本向量化适配器。
 * 返回前仍由本适配器校验数量、索引、维度和数值有效性，维护知识模块的稳定业务契约。
 */
@Slf4j
public class SpringAiEmbeddingService implements EmbeddingService {
    private static final String EMBEDDINGS_PATH = "/embeddings";
    private static final String LOCAL_NO_AUTH_KEY = "not-required";

    private final EmbeddingProperties properties;
    private final org.springframework.ai.embedding.EmbeddingModel delegate;

    public SpringAiEmbeddingService(EmbeddingProperties properties) {
        this(properties, ObservationRegistry.NOOP);
    }

    public SpringAiEmbeddingService(EmbeddingProperties properties, ObservationRegistry observationRegistry) {
        this.properties = properties;
        OpenAiEmbeddingOptions.Builder options = OpenAiEmbeddingOptions.builder()
                .baseUrl(baseUrl(properties.getEndpoint()))
                .apiKey(apiKey(properties.getApiKey()))
                .model(properties.getModel().trim())
                .timeout(Duration.ofMillis(properties.getRequestTimeoutMillis()))
                .maxRetries(Math.max(0, properties.getMaxAttempts() - 1));
        if (properties.isSendDimensions()) {
            options.dimensions(properties.getDimensions());
        }
        this.delegate = OpenAiEmbeddingModel.builder()
                .options(options.build())
                .observationRegistry(observationRegistry)
                .build();
    }

    SpringAiEmbeddingService(EmbeddingProperties properties,
                             org.springframework.ai.embedding.EmbeddingModel delegate) {
        this.properties = properties;
        this.delegate = delegate;
    }

    @Override
    public List<List<Float>> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        if (texts.stream().anyMatch(text -> text == null || text.isBlank())) {
            throw new EmbeddingException("Embedding 输入文本不能为空");
        }
        try {
            EmbeddingResponse response = delegate.embedForResponse(texts);
            return validate(response, texts.size());
        } catch (EmbeddingException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("Spring AI Embedding 模型调用失败: model={}, inputCount={}",
                    properties.getModel(), texts.size(), e);
            throw new EmbeddingException("Embedding 服务调用失败", e);
        }
    }

    @Override
    public int dimensions() {
        return properties.getDimensions();
    }

    private List<List<Float>> validate(EmbeddingResponse response, int expectedCount) {
        List<Embedding> embeddings = response == null ? null : response.getResults();
        if (embeddings == null || embeddings.size() != expectedCount) {
            throw new EmbeddingException("Embedding 返回数量与请求数量不一致");
        }
        List<Embedding> ordered = new ArrayList<>(embeddings);
        ordered.sort(Comparator.comparingInt(value -> value.getIndex() == null ? -1 : value.getIndex()));
        List<List<Float>> result = new ArrayList<>(ordered.size());
        for (int index = 0; index < ordered.size(); index++) {
            Embedding embedding = ordered.get(index);
            if (embedding.getIndex() == null || embedding.getIndex() != index) {
                throw new EmbeddingException("Embedding 返回索引不连续");
            }
            float[] output = embedding.getOutput();
            if (output == null || output.length != dimensions()) {
                throw new EmbeddingException("Embedding 返回向量维度不符合配置");
            }
            List<Float> vector = new ArrayList<>(output.length);
            for (float value : output) {
                if (!Float.isFinite(value)) {
                    throw new EmbeddingException("Embedding 返回向量包含非有限数值");
                }
                vector.add(value);
            }
            result.add(List.copyOf(vector));
        }
        return List.copyOf(result);
    }

    static String baseUrl(String endpoint) {
        String normalized = endpoint.trim().replaceAll("/+$", "");
        if (!normalized.endsWith(EMBEDDINGS_PATH)) {
            throw new IllegalArgumentException("Embedding endpoint 必须以 /embeddings 结尾");
        }
        return normalized.substring(0, normalized.length() - EMBEDDINGS_PATH.length());
    }

    private String apiKey(String configured) {
        return configured == null || configured.isBlank() ? LOCAL_NO_AUTH_KEY : configured.trim();
    }
}
