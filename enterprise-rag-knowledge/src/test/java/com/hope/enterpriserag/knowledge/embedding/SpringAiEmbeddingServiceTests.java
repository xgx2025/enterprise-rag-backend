package com.hope.enterpriserag.knowledge.embedding;

import com.hope.enterpriserag.knowledge.config.EmbeddingProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SpringAiEmbeddingServiceTests {
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void restoresEmbeddingOrderUsingSpringAiResponseIndexes() throws IOException {
        startServer("""
                {"object":"list","model":"test-embedding","data":[
                  {"object":"embedding","index":1,"embedding":[0.4,0.5,0.6]},
                  {"object":"embedding","index":0,"embedding":[0.1,0.2,0.3]}
                ],"usage":{"prompt_tokens":2,"total_tokens":2}}
                """);
        SpringAiEmbeddingService service = new SpringAiEmbeddingService(properties(3));

        List<List<Float>> result = service.embed(List.of("第一段", "第二段"));

        assertEquals(List.of(0.1F, 0.2F, 0.3F), result.get(0));
        assertEquals(List.of(0.4F, 0.5F, 0.6F), result.get(1));
    }

    @Test
    void rejectsVectorWhoseDimensionDoesNotMatchCollectionConfiguration() throws IOException {
        startServer("""
                {"object":"list","model":"test-embedding","data":[
                  {"object":"embedding","index":0,"embedding":[0.1,0.2]}
                ],"usage":{"prompt_tokens":1,"total_tokens":1}}
                """);
        SpringAiEmbeddingService service = new SpringAiEmbeddingService(properties(3));

        assertThrows(EmbeddingException.class, () -> service.embed(List.of("测试文本")));
    }

    @Test
    void derivesBaseUrlFromFullEmbeddingEndpoint() {
        assertEquals("https://example.com/compatible-mode/v1",
                SpringAiEmbeddingService.baseUrl("https://example.com/compatible-mode/v1/embeddings/"));
    }

    private void startServer(String responseBody) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/embeddings", exchange -> respond(exchange, responseBody));
        server.start();
    }

    private void respond(HttpExchange exchange, String responseBody) throws IOException {
        exchange.getRequestBody().readAllBytes();
        byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private EmbeddingProperties properties(int dimensions) {
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setEndpoint("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/embeddings");
        properties.setModel("test-embedding");
        properties.setDimensions(dimensions);
        properties.setMaxAttempts(1);
        properties.setRequestTimeoutMillis(2_000);
        return properties;
    }
}
