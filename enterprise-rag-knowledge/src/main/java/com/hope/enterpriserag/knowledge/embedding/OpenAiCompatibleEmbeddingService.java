package com.hope.enterpriserag.knowledge.embedding;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hope.enterpriserag.knowledge.config.EmbeddingProperties;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 基于 OpenAI {@code /embeddings} 协议的模型适配器，可连接云端或本地兼容服务。
 * 请求和响应正文仅在内存中处理，任何日志与异常都不会包含文档内容或 API Key。
 */
@Slf4j
public class OpenAiCompatibleEmbeddingService implements EmbeddingService {
    private final EmbeddingProperties properties;
    private final HttpClient httpClient;
    private final Gson gson = new Gson();
    private final URI endpoint;

    public OpenAiCompatibleEmbeddingService(EmbeddingProperties properties) {
        this.properties = properties;
        this.endpoint = URI.create(properties.getEndpoint().trim());
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMillis()))
                .build();
    }

    @Override
    public List<List<Float>> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        if (texts.stream().anyMatch(text -> text == null || text.isBlank())) {
            throw new EmbeddingException("Embedding 输入包含空文本");
        }

        String requestBody = createRequestBody(texts);
        for (int attempt = 1; attempt <= properties.getMaxAttempts(); attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(createRequest(requestBody),
                        HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return parseResponse(response.body(), texts.size());
                }
                if (!isRetryable(response.statusCode()) || attempt == properties.getMaxAttempts()) {
                    throw new EmbeddingException("Embedding 服务返回 HTTP " + response.statusCode());
                }
                log.warn("Embedding 服务暂时不可用，准备重试: model={}, httpStatus={}, attempt={}, maxAttempts={}",
                        properties.getModel(), response.statusCode(), attempt, properties.getMaxAttempts());
            } catch (IOException e) {
                if (attempt == properties.getMaxAttempts()) {
                    throw new EmbeddingException("Embedding 服务网络调用失败", e);
                }
                log.warn("Embedding 服务网络调用失败，准备重试: model={}, attempt={}, maxAttempts={}",
                        properties.getModel(), attempt, properties.getMaxAttempts(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new EmbeddingException("Embedding 请求被中断", e);
            }
            waitBeforeRetry(attempt);
        }
        throw new EmbeddingException("Embedding 服务调用失败");
    }

    @Override
    public int dimensions() {
        return properties.getDimensions();
    }

    private HttpRequest createRequest(String requestBody) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofMillis(properties.getRequestTimeoutMillis()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody));
        if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
            builder.header("Authorization", "Bearer " + properties.getApiKey().trim());
        }
        return builder.build();
    }

    private String createRequestBody(List<String> texts) {
        JsonObject request = new JsonObject();
        request.addProperty("model", properties.getModel().trim());
        request.add("input", gson.toJsonTree(texts));
        if (properties.isSendDimensions()) {
            request.addProperty("dimensions", properties.getDimensions());
        }
        return gson.toJson(request);
    }

    private List<List<Float>> parseResponse(String responseBody, int expectedCount) {
        try {
            JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
            JsonArray data = root.getAsJsonArray("data");
            if (data == null || data.size() != expectedCount) {
                throw new EmbeddingException("Embedding 返回数量与请求数量不一致");
            }

            List<IndexedVector> indexedVectors = new ArrayList<>(data.size());
            for (int position = 0; position < data.size(); position++) {
                JsonObject item = data.get(position).getAsJsonObject();
                int index = item.has("index") ? item.get("index").getAsInt() : position;
                JsonArray embedding = item.getAsJsonArray("embedding");
                if (embedding == null || embedding.size() != dimensions()) {
                    throw new EmbeddingException("Embedding 返回向量维度不符合配置");
                }
                List<Float> vector = new ArrayList<>(embedding.size());
                for (JsonElement value : embedding) {
                    float number = value.getAsFloat();
                    if (!Float.isFinite(number)) {
                        throw new EmbeddingException("Embedding 返回向量包含非有限数值");
                    }
                    vector.add(number);
                }
                indexedVectors.add(new IndexedVector(index, List.copyOf(vector)));
            }
            indexedVectors.sort(Comparator.comparingInt(IndexedVector::index));
            for (int index = 0; index < indexedVectors.size(); index++) {
                if (indexedVectors.get(index).index() != index) {
                    throw new EmbeddingException("Embedding 返回索引不连续");
                }
            }
            return indexedVectors.stream().map(IndexedVector::vector).toList();
        } catch (EmbeddingException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new EmbeddingException("Embedding 服务返回格式无效", e);
        }
    }

    private boolean isRetryable(int statusCode) {
        return statusCode == 408 || statusCode == 429 || statusCode >= 500;
    }

    private void waitBeforeRetry(int attempt) {
        long multiplier = 1L << Math.min(attempt - 1, 10);
        long delay = Math.min(properties.getRetryDelayMillis() * multiplier, 30_000L);
        if (delay <= 0) {
            return;
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EmbeddingException("Embedding 重试等待被中断", e);
        }
    }

    private record IndexedVector(int index, List<Float> vector) {
    }
}
