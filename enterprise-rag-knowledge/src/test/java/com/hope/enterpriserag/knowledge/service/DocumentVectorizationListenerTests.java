package com.hope.enterpriserag.knowledge.service;

import com.hope.enterpriserag.knowledge.config.VectorizationProperties;
import com.hope.enterpriserag.knowledge.embedding.EmbeddingException;
import com.hope.enterpriserag.knowledge.embedding.EmbeddingService;
import com.hope.enterpriserag.knowledge.entity.DocumentChunk;
import com.hope.enterpriserag.knowledge.entity.IngestionTask;
import com.hope.enterpriserag.knowledge.entity.KnowledgeDocument;
import com.hope.enterpriserag.knowledge.event.DocumentVectorizationEvent;
import com.hope.enterpriserag.knowledge.mapper.DocumentChunkMapper;
import com.hope.enterpriserag.knowledge.mapper.IngestionTaskMapper;
import com.hope.enterpriserag.knowledge.mapper.KnowledgeDocumentMapper;
import com.hope.enterpriserag.knowledge.vector.VectorStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DocumentVectorizationListenerTests {
    private KnowledgeDocumentMapper documentMapper;
    private DocumentChunkMapper chunkMapper;
    private IngestionTaskMapper taskMapper;
    private EmbeddingService embeddingService;
    private VectorStore vectorStore;
    private DocumentVectorizationListener listener;
    private KnowledgeDocument document;
    private IngestionTask task;

    @BeforeEach
    void setUp() {
        documentMapper = mock(KnowledgeDocumentMapper.class);
        chunkMapper = mock(DocumentChunkMapper.class);
        taskMapper = mock(IngestionTaskMapper.class);
        embeddingService = mock(EmbeddingService.class);
        vectorStore = mock(VectorStore.class);
        VectorizationProperties properties = new VectorizationProperties();
        properties.setBatchSize(2);
        listener = new DocumentVectorizationListener(documentMapper, chunkMapper, taskMapper,
                embeddingService, vectorStore, properties);

        document = document();
        task = task();
        when(documentMapper.selectById(100L)).thenReturn(document);
        when(taskMapper.selectById(200L)).thenReturn(task);
        when(taskMapper.update(any(), any())).thenReturn(1);
        when(embeddingService.dimensions()).thenReturn(3);
    }

    @Test
    void vectorizesChildChunksInBatchesAndCompletesTask() {
        when(chunkMapper.selectList(any())).thenReturn(List.of(chunk(1L), chunk(2L), chunk(3L)));
        when(embeddingService.embed(any())).thenAnswer(invocation -> {
            List<String> texts = invocation.getArgument(0);
            return texts.stream().map(ignored -> List.of(0.1F, 0.2F, 0.3F)).toList();
        });

        listener.process(new DocumentVectorizationEvent(100L, 200L));

        verify(vectorStore).ensureReady(3);
        verify(vectorStore).deleteDocument(10L, 100L);
        verify(vectorStore, times(2)).upsert(any());
        assertEquals("READY", document.getStatus());
        assertEquals("COMPLETED", document.getEmbeddingStatus());
        assertEquals(100, document.getProcessProgress());
        assertEquals("SUCCEEDED", task.getStatus());
        assertEquals("COMPLETED", task.getCurrentStage());
        assertEquals(100, task.getProgress());
    }

    @Test
    void recordsEmbeddingFailureWithoutLoggingOrPersistingDocumentContent() {
        when(chunkMapper.selectList(any())).thenReturn(List.of(chunk(1L)));
        when(embeddingService.embed(any())).thenThrow(new EmbeddingException("外部服务不可用"));

        listener.process(new DocumentVectorizationEvent(100L, 200L));

        verify(vectorStore).deleteDocument(10L, 100L);
        assertEquals("FAILED", document.getStatus());
        assertEquals("FAILED", document.getEmbeddingStatus());
        assertEquals("EMBEDDING", document.getFailureStage());
        assertEquals("FAILED", task.getStatus());
        assertEquals("EMBEDDING", task.getCurrentStage());
    }

    @Test
    void ignoresDuplicateEventWhenTaskCannotBeClaimed() {
        when(taskMapper.update(any(), any())).thenReturn(0);

        listener.process(new DocumentVectorizationEvent(100L, 200L));

        verifyNoInteractions(vectorStore);
        verify(embeddingService, times(0)).embed(any());
        assertEquals("WAITING_VECTOR", task.getStatus());
    }

    private KnowledgeDocument document() {
        KnowledgeDocument value = new KnowledgeDocument();
        value.setId(100L);
        value.setTenantId(10L);
        value.setKnowledgeBaseId(20L);
        value.setVersion("V1.0");
        value.setStatus("PROCESSING");
        value.setDepartment("HR");
        value.setSecurityLevel(2);
        value.setAuthorityLevel(3);
        value.setAllowedRoles("[\"EMPLOYEE\"]");
        value.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        value.setParseStatus("COMPLETED");
        value.setEmbeddingStatus("PENDING");
        value.setProcessProgress(70);
        value.setDeleted(0);
        return value;
    }

    private IngestionTask task() {
        IngestionTask value = new IngestionTask();
        value.setId(200L);
        value.setTenantId(10L);
        value.setDocumentId(100L);
        value.setStatus("WAITING_VECTOR");
        value.setProgress(70);
        return value;
    }

    private DocumentChunk chunk(Long id) {
        DocumentChunk value = new DocumentChunk();
        value.setId(id);
        value.setTenantId(10L);
        value.setDocumentId(100L);
        value.setParentChunkId(1000L);
        value.setChunkIndex(id.intValue());
        value.setContent("仅用于测试的分块 " + id);
        value.setSectionPath("正文/子块 " + id);
        value.setEmbeddingStatus("PENDING");
        return value;
    }
}
