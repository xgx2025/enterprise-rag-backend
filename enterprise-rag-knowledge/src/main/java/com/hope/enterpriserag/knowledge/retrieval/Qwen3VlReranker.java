package com.hope.enterpriserag.knowledge.retrieval;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hope.enterpriserag.knowledge.config.RerankProperties;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 阿里云百炼 Qwen3-VL-Rerank HTTP 适配器。
 * 当前检索链路将查询与已授权父块作为纯文本模态发送；请求和响应正文不会写入日志或异常消息。
 */
@Slf4j
public class Qwen3VlReranker implements Reranker {
    private final RerankProperties properties;
    private final Reranker fallback;
    private final HttpClient httpClient;
    private final Gson gson = new Gson();
    private final URI endpoint;

    public Qwen3VlReranker(RerankProperties properties, Reranker fallback) {
        this.properties = properties;
        this.fallback = fallback;
        this.endpoint = URI.create(properties.getEndpoint().trim());
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMillis()))
                .build();
    }

    @Override
    public List<RetrievedChunk> rerank(String query, List<RetrievedChunk> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        try {
            return invokeModel(query, candidates);
        } catch (RerankException e) {
            if (Thread.currentThread().isInterrupted() || !properties.isFallbackEnabled()) {
                throw e;
            }
            log.error("Qwen3-VL-Rerank 调用失败，降级到启发式重排: model={}, candidateCount={}",
                    properties.getModel(), candidates.size(), e);
            return fallback.rerank(query, candidates);
        }
    }

    private List<RetrievedChunk> invokeModel(String query, List<RetrievedChunk> candidates) {
        String requestBody = createRequestBody(query, candidates);
        for (int attempt = 1; attempt <= properties.getMaxAttempts(); attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(createRequest(requestBody),
                        HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return parseResponse(response.body(), candidates);
                }
                if (!isRetryable(response.statusCode()) || attempt == properties.getMaxAttempts()) {
                    throw new RerankException("Qwen3-VL-Rerank 服务返回 HTTP " + response.statusCode());
                }
                log.warn("Qwen3-VL-Rerank 服务暂时不可用，准备重试: model={}, httpStatus={}, attempt={}, maxAttempts={}, candidateCount={}",
                        properties.getModel(), response.statusCode(), attempt, properties.getMaxAttempts(),
                        candidates.size());
            } catch (IOException e) {
                if (attempt == properties.getMaxAttempts()) {
                    throw new RerankException("Qwen3-VL-Rerank 网络调用失败", e);
                }
                log.warn("Qwen3-VL-Rerank 网络调用失败，准备重试: model={}, attempt={}, maxAttempts={}, candidateCount={}",
                        properties.getModel(), attempt, properties.getMaxAttempts(), candidates.size(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RerankException("Qwen3-VL-Rerank 请求被中断", e);
            }
            waitBeforeRetry(attempt);
        }
        throw new RerankException("Qwen3-VL-Rerank 调用失败");
    }

    private HttpRequest createRequest(String requestBody) {
        return HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofMillis(properties.getRequestTimeoutMillis()))
                .header("Authorization", "Bearer " + properties.getApiKey().trim())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
    }

    private String createRequestBody(String query, List<RetrievedChunk> candidates) {
        if (query == null || query.isBlank()) {
            throw new RerankException("Qwen3-VL-Rerank 查询不能为空");
        }
        JsonObject request = new JsonObject();
        request.addProperty("model", properties.getModel().trim());

        JsonObject input = new JsonObject();
        JsonObject queryObject = new JsonObject();
        queryObject.addProperty("text", query.trim());
        input.add("query", queryObject);
        JsonArray documents = new JsonArray();
        for (RetrievedChunk candidate : candidates) {
            String content = candidate.parentContent() == null
                    ? candidate.childContent() : candidate.parentContent();
            if (content == null || content.isBlank()) {
                throw new RerankException("Qwen3-VL-Rerank 候选包含空正文");
            }
            JsonObject document = new JsonObject();
            document.addProperty("text", content);
            documents.add(document);
        }
        input.add("documents", documents);
        request.add("input", input);

        JsonObject parameters = new JsonObject();
        parameters.addProperty("return_documents", false);
        parameters.addProperty("top_n", candidates.size());
        if (properties.getInstruct() != null && !properties.getInstruct().isBlank()) {
            parameters.addProperty("instruct", properties.getInstruct().trim());
        }
        request.add("parameters", parameters);
        return gson.toJson(request);
    }

    private List<RetrievedChunk> parseResponse(String responseBody, List<RetrievedChunk> candidates) {
        try {
            JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
            JsonObject output = root.getAsJsonObject("output");
            JsonArray results = output == null ? null : output.getAsJsonArray("results");
            if (results == null || results.size() != candidates.size()) {
                throw new RerankException("Qwen3-VL-Rerank 返回数量与请求数量不一致");
            }

            Set<Integer> indexes = new HashSet<>();
            List<RetrievedChunk> reranked = new ArrayList<>(results.size());
            for (JsonElement element : results) {
                JsonObject result = element.getAsJsonObject();
                if (!result.has("index") || !result.has("relevance_score")) {
                    throw new RerankException("Qwen3-VL-Rerank 返回字段不完整");
                }
                int index = result.get("index").getAsInt();
                double score = result.get("relevance_score").getAsDouble();
                if (index < 0 || index >= candidates.size() || !indexes.add(index)) {
                    throw new RerankException("Qwen3-VL-Rerank 返回索引无效");
                }
                if (!Double.isFinite(score)) {
                    throw new RerankException("Qwen3-VL-Rerank 返回分数无效");
                }
                reranked.add(candidates.get(index).withRerankScore(score));
            }
            return reranked.stream()
                    .sorted(Comparator.comparingDouble(RetrievedChunk::rerankScore).reversed()
                            .thenComparing(RetrievedChunk::childChunkId))
                    .toList();
        } catch (RerankException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new RerankException("Qwen3-VL-Rerank 服务返回格式无效", e);
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
            throw new RerankException("Qwen3-VL-Rerank 重试等待被中断", e);
        }
    }
}
