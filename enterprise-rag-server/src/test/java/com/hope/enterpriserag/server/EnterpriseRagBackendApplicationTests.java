package com.hope.enterpriserag.server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.sql.init.mode=never",
    "rag.vectorization.enabled=true",
    "rag.embedding.endpoint=http://127.0.0.1:9/embeddings",
    "rag.embedding.model=context-test-embedding",
    "rag.embedding.dimensions=8",
    "rag.milvus.uri=http://127.0.0.1:19530",
    "rag.chat.enabled=true",
    "rag.chat.model.endpoint=http://127.0.0.1:9/chat/completions",
    "rag.chat.model.api-key=context-test-key",
    "rag.chat.model.model=context-test-model"
})
class EnterpriseRagBackendApplicationTests {

    @Test
    void contextLoads() {
    }
}
