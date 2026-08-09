package com.hope.enterpriserag.knowledge.retrieval;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hope.enterpriserag.knowledge.config.RerankProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Qwen3VlRerankerTests {
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void mapsScoresByIndexesAndUsesOfficialRequestShape() throws IOException {
        startServer(200, """
                {"output":{"results":[
                  {"index":1,"relevance_score":0.93},
                  {"index":0,"relevance_score":0.21}
                ]}}
                """);
        Qwen3VlReranker reranker = new Qwen3VlReranker(properties(false), new HeuristicReranker());

        List<RetrievedChunk> result = reranker.rerank("深圳住宿标准",
                List.of(candidate(1L, "员工请假流程"), candidate(2L, "深圳住宿标准为每天600元")));

        assertEquals(2L, result.getFirst().childChunkId());
        assertEquals(0.93, result.getFirst().rerankScore());
        assertEquals("Bearer test-api-key", authorization.get());

        JsonObject request = JsonParser.parseString(requestBody.get()).getAsJsonObject();
        assertEquals("qwen3-vl-rerank", request.get("model").getAsString());
        assertEquals("深圳住宿标准", request.getAsJsonObject("input")
                .getAsJsonObject("query").get("text").getAsString());
        assertEquals(2, request.getAsJsonObject("input").getAsJsonArray("documents").size());
        assertEquals(2, request.getAsJsonObject("parameters").get("top_n").getAsInt());
        assertFalse(request.getAsJsonObject("parameters").get("return_documents").getAsBoolean());
    }

    @Test
    void fallsBackToHeuristicRerankerWhenServiceFails() throws IOException {
        startServer(503, "{} ");
        Reranker fallback = (query, candidates) ->
                List.of(candidates.getFirst().withRerankScore(0.42));
        Qwen3VlReranker reranker = new Qwen3VlReranker(properties(true), fallback);

        List<RetrievedChunk> result = reranker.rerank("住宿标准",
                List.of(candidate(1L, "深圳住宿标准为每天600元")));

        assertEquals(1, result.size());
        assertEquals(0.42, result.getFirst().rerankScore());
    }

    @Test
    void rejectsIncompleteResponseWhenFallbackIsDisabled() throws IOException {
        startServer(200, """
                {"output":{"results":[{"index":0,"relevance_score":0.8}]}}
                """);
        Qwen3VlReranker reranker = new Qwen3VlReranker(properties(false), new HeuristicReranker());

        assertThrows(RerankException.class, () -> reranker.rerank("住宿标准",
                List.of(candidate(1L, "第一条"), candidate(2L, "第二条"))));
    }

    private void startServer(int statusCode, String responseBody) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/rerank", exchange -> respond(exchange, statusCode, responseBody));
        server.start();
    }

    private void respond(HttpExchange exchange, int statusCode, String responseBody) throws IOException {
        authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private RerankProperties properties(boolean fallbackEnabled) {
        RerankProperties properties = new RerankProperties();
        properties.setModelEnabled(true);
        properties.setEndpoint("http://127.0.0.1:" + server.getAddress().getPort() + "/rerank");
        properties.setApiKey("test-api-key");
        properties.setFallbackEnabled(fallbackEnabled);
        properties.setMaxAttempts(1);
        properties.setConnectTimeoutMillis(1_000);
        properties.setRequestTimeoutMillis(2_000);
        return properties;
    }

    private RetrievedChunk candidate(Long id, String parentContent) {
        return new RetrievedChunk(id, id + 1000, 100L, 20L, "差旅制度", "V1.0", 1, 2,
                LocalDate.of(2026, 1, 1), "第三章", 1, id.intValue(), "子块", parentContent,
                0.6, 0.5, 0.4, 0.0);
    }
}
