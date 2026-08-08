package com.hope.enterpriserag.knowledge.vector;

import com.hope.enterpriserag.knowledge.config.MilvusProperties;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.response.DescribeCollectionResp;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.response.SearchResp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MilvusVectorStoreTests {
    private MilvusClientV2 client;
    private MilvusVectorStore vectorStore;

    @BeforeEach
    void setUp() {
        client = mock(MilvusClientV2.class);
        MilvusProperties properties = new MilvusProperties();
        properties.setDatabaseName("default");
        properties.setCollectionName("test_chunks");
        vectorStore = new MilvusVectorStore(client, properties);
    }

    @Test
    void createsCollectionAndDoesNotWriteDocumentContent() {
        when(client.hasCollection(any())).thenReturn(false);
        vectorStore.ensureReady(3);

        vectorStore.upsert(List.of(record()));

        verify(client).createCollection(any());
        ArgumentCaptor<UpsertReq> requestCaptor = ArgumentCaptor.forClass(UpsertReq.class);
        verify(client).upsert(requestCaptor.capture());
        assertFalse(requestCaptor.getValue().getData().getFirst().has("content"));
    }

    @Test
    void refusesExistingCollectionWithDifferentDimensions() {
        when(client.hasCollection(any())).thenReturn(true);
        CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder().build();
        schema.addField(AddFieldReq.builder()
                .fieldName("embedding")
                .dataType(DataType.FloatVector)
                .dimension(4)
                .build());
        DescribeCollectionResp response = DescribeCollectionResp.builder()
                .collectionSchema(schema)
                .build();
        when(client.describeCollection(any())).thenReturn(response);

        assertThrows(IllegalStateException.class, () -> vectorStore.ensureReady(3));
    }

    @Test
    void searchesWithServerSideGovernanceFilterAndReturnsOnlyIdentifiers() {
        when(client.hasCollection(any())).thenReturn(false);
        SearchResp.SearchResult result = SearchResp.SearchResult.builder()
                .id(1L)
                .score(0.87F)
                .entity(Map.of("document_id", 100L, "parent_chunk_id", 1000L, "chunk_index", 3L))
                .build();
        when(client.search(any())).thenReturn(SearchResp.builder()
                .searchResults(List.of(List.of(result)))
                .build());
        vectorStore.ensureReady(3);

        List<VectorSearchHit> hits = vectorStore.search(new VectorSearchRequest(
                10L, List.of(20L, 21L), 1, LocalDate.of(2026, 8, 8), 10,
                List.of(0.1F, 0.2F, 0.3F)));

        ArgumentCaptor<SearchReq> requestCaptor = ArgumentCaptor.forClass(SearchReq.class);
        verify(client).search(requestCaptor.capture());
        SearchReq request = requestCaptor.getValue();
        assertTrue(request.getFilter().contains("tenant_id == {tenantId}"));
        assertTrue(request.getFilter().contains("document_status == {documentStatus}"));
        assertTrue(request.getFilter().contains("security_level <= {maximumSecurityLevel}"));
        assertEquals(List.of(20L, 21L), request.getFilterTemplateValues().get("knowledgeBaseIds"));
        assertEquals(1L, hits.getFirst().chunkId());
        assertEquals(1000L, hits.getFirst().parentChunkId());
        assertEquals(0.87, hits.getFirst().score(), 0.0001);
    }

    private VectorRecord record() {
        return new VectorRecord(
                1L, 10L, 20L, 100L, 1000L, 1, "V1.0", "READY", "HR",
                2, 3, LocalDate.of(2026, 1, 1), null, "[\"EMPLOYEE\"]",
                "正文/子块1", 1, List.of(0.1F, 0.2F, 0.3F));
    }
}
