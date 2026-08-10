package com.hope.enterpriserag.chat.model;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hope.enterpriserag.chat.config.ChatModelProperties;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * OpenAI {@code /chat/completions} 兼容模型适配器。
 * Prompt 与响应正文只在内存中处理，日志仅记录模型、状态码和重试次数。
 */
@Slf4j
public class OpenAiCompatibleChatModel implements ChatModel {
    private final ChatModelProperties properties;
    private final HttpClient httpClient;
    private final Gson gson = new Gson();
    private final URI endpoint;

    public OpenAiCompatibleChatModel(ChatModelProperties properties) {
        this.properties = properties;
        this.endpoint = URI.create(properties.getEndpoint().trim());
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMillis()))
                .build();
    }

    @Override
    public ChatModelResult generate(ChatModelPrompt prompt) {
        if (prompt == null || prompt.systemPrompt() == null || prompt.userPrompt() == null) {
            throw new ChatModelException("Chat 模型 Prompt 不能为空");
        }
        String requestBody = requestBody(prompt);
        for (int attempt = 1; attempt <= properties.getMaxAttempts(); attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(request(requestBody),
                        HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return parse(response.body());
                }
                if (!retryable(response.statusCode()) || attempt == properties.getMaxAttempts()) {
                    throw new ChatModelException("Chat 模型服务返回 HTTP " + response.statusCode());
                }
                log.warn("Chat 模型服务暂时不可用，准备重试: model={}, httpStatus={}, attempt={}, maxAttempts={}",
                        modelName(), response.statusCode(), attempt, properties.getMaxAttempts());
            } catch (IOException e) {
                if (attempt == properties.getMaxAttempts()) {
                    throw new ChatModelException("Chat 模型网络调用失败", e);
                }
                log.warn("Chat 模型网络调用失败，准备重试: model={}, attempt={}, maxAttempts={}",
                        modelName(), attempt, properties.getMaxAttempts(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ChatModelException("Chat 模型请求被中断", e);
            }
            waitBeforeRetry(attempt);
        }
        throw new ChatModelException("Chat 模型调用失败");
    }

    @Override
    public String modelName() {
        return properties.getModel().trim();
    }

    private HttpRequest request(String body) {
        return HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofMillis(properties.getRequestTimeoutMillis()))
                .header("Authorization", "Bearer " + properties.getApiKey().trim())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    private String requestBody(ChatModelPrompt prompt) {
        JsonObject root = new JsonObject();
        root.addProperty("model", modelName());
        JsonArray messages = new JsonArray();
        messages.add(message("system", prompt.systemPrompt()));
        messages.add(message("user", prompt.userPrompt()));
        root.add("messages", messages);
        root.addProperty("temperature", properties.getTemperature());
        root.addProperty("max_tokens", properties.getMaxTokens());
        root.addProperty("stream", false);
        return gson.toJson(root);
    }

    private JsonObject message(String role, String content) {
        JsonObject value = new JsonObject();
        value.addProperty("role", role);
        value.addProperty("content", content);
        return value;
    }

    private ChatModelResult parse(String body) {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices == null || choices.isEmpty()) {
                throw new ChatModelException("Chat 模型返回结果为空");
            }
            JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
            String content = message == null || !message.has("content")
                    ? null : message.get("content").getAsString();
            if (content == null || content.isBlank()) {
                throw new ChatModelException("Chat 模型返回回答为空");
            }
            JsonObject usage = root.getAsJsonObject("usage");
            int promptTokens = integer(usage, "prompt_tokens");
            int completionTokens = integer(usage, "completion_tokens");
            int totalTokens = integer(usage, "total_tokens");
            return new ChatModelResult(content.trim(), promptTokens, completionTokens, totalTokens);
        } catch (ChatModelException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ChatModelException("Chat 模型返回格式无效", e);
        }
    }

    private int integer(JsonObject object, String name) {
        return object != null && object.has(name) ? object.get(name).getAsInt() : 0;
    }

    private boolean retryable(int statusCode) {
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
            throw new ChatModelException("Chat 模型重试等待被中断", e);
        }
    }
}
