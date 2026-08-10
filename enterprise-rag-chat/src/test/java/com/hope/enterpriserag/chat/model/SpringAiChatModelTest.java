package com.hope.enterpriserag.chat.model;

import com.hope.enterpriserag.chat.config.ChatModelProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class SpringAiChatModelTest {
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldDelegateOpenAiCompatibleRequestToSpringAiAndMapUsage() throws IOException {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = """
                    {"id":"chatcmpl-test","object":"chat.completion","created":1,"model":"test-model",
                     "choices":[{"index":0,"message":{"role":"assistant","content":"可信回答。[S1]"},"finish_reason":"stop"}],
                     "usage":{"prompt_tokens":11,"completion_tokens":7,"total_tokens":18}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        ChatModelProperties properties = properties();
        SpringAiChatModel model = new SpringAiChatModel(properties);
        ChatModelResult result = model.generate(new ChatModelPrompt("system rule", "question and evidence"));

        assertThat(authorization.get()).isEqualTo("Bearer test-secret");
        assertThat(requestBody.get()).contains("test-model", "system rule", "question and evidence");
        assertThat(result).isEqualTo(new ChatModelResult("可信回答。[S1]", 11, 7, 18));
    }

    @Test
    void shouldDeriveBaseUrlFromFullCompletionsEndpoint() {
        assertThat(SpringAiChatModel.baseUrl("https://example.com/compatible-mode/v1/chat/completions/",
                "/chat/completions")).isEqualTo("https://example.com/compatible-mode/v1");
    }

    private ChatModelProperties properties() {
        ChatModelProperties properties = new ChatModelProperties();
        properties.setEndpoint("http://127.0.0.1:" + server.getAddress().getPort() + "/chat/completions");
        properties.setApiKey("test-secret");
        properties.setModel("test-model");
        properties.setRequestTimeoutMillis(2_000);
        properties.setMaxAttempts(1);
        return properties;
    }
}
