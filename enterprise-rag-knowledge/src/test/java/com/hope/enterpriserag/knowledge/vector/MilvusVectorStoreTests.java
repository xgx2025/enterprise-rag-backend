package com.hope.enterpriserag.knowledge.vector;

import com.hope.enterpriserag.knowledge.config.MilvusProperties;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.response.DescribeCollectionResp;
import io.milvus.v2.service.vector.request.UpsertReq;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    private VectorRecord record() {
        return new VectorRecord(
                1L, 10L, 20L, 100L, 1000L, 1, "V1.0", "READY", "HR",
                2, 3, LocalDate.of(2026, 1, 1), null, "[\"EMPLOYEE\"]",
                "正文/子块1", 1, List.of(0.1F, 0.2F, 0.3F));
    }
}
