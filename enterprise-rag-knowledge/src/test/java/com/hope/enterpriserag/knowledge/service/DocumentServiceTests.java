package com.hope.enterpriserag.knowledge.service;

import com.hope.enterpriserag.common.exception.BusinessException;
import com.hope.enterpriserag.knowledge.entity.KnowledgeDocument;
import com.hope.enterpriserag.knowledge.event.DocumentVectorizationEvent;
import com.hope.enterpriserag.knowledge.mapper.DocumentChunkMapper;
import com.hope.enterpriserag.knowledge.mapper.IngestionTaskMapper;
import com.hope.enterpriserag.knowledge.mapper.KnowledgeDocumentMapper;
import com.hope.enterpriserag.knowledge.storage.ObjectStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentServiceTests {
    private KnowledgeDocumentMapper documentMapper;
    private DocumentChunkMapper chunkMapper;
    private IngestionTaskMapper taskMapper;
    private ObjectStorageService storageService;
    private ApplicationEventPublisher eventPublisher;
    private DocumentService documentService;

    @BeforeEach
    void setUp() {
        documentMapper = mock(KnowledgeDocumentMapper.class);
        chunkMapper = mock(DocumentChunkMapper.class);
        taskMapper = mock(IngestionTaskMapper.class);
        storageService = mock(ObjectStorageService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        documentService = new DocumentService(
                documentMapper,
                chunkMapper,
                taskMapper,
                mock(KnowledgeBaseService.class),
                storageService,
                eventPublisher);
    }

    @Test
    void publishesReadyParsedDocument() {
        KnowledgeDocument document = document("READY");
        document.setParseStatus("COMPLETED");
        document.setEmbeddingStatus("COMPLETED");
        when(documentMapper.selectOne(any())).thenReturn(document);

        documentService.updateStatus(10L, 100L, "ACTIVE");

        assertEquals("ACTIVE", document.getStatus());
        verify(documentMapper).updateById(document);
    }

    @Test
    void refusesToArchiveActiveDocument() {
        KnowledgeDocument document = document("ACTIVE");
        when(documentMapper.selectOne(any())).thenReturn(document);

        assertThrows(BusinessException.class, () -> documentService.archive(10L, 100L));

        verify(documentMapper, never()).updateById(any(KnowledgeDocument.class));
    }

    @Test
    void generatesShortLivedPreviewUrlForOwnedDocument() {
        KnowledgeDocument document = document("READY");
        document.setObjectKey("enterprise-rag/10/20/100/policy.pdf");
        when(documentMapper.selectOne(any())).thenReturn(document);
        when(storageService.readUrlExpiration()).thenReturn(Duration.ofMinutes(10));
        when(storageService.generateReadUrl(document.getObjectKey(), Duration.ofMinutes(10)))
                .thenReturn("https://example.oss-cn-hangzhou.aliyuncs.com/signed");

        var result = documentService.previewUrl(10L, 100L);

        assertEquals("https://example.oss-cn-hangzhou.aliyuncs.com/signed", result.url());
    }

    @Test
    void retriesOnlyVectorizationAfterEmbeddingFailure() {
        KnowledgeDocument document = document("FAILED");
        document.setParseStatus("COMPLETED");
        document.setEmbeddingStatus("FAILED");
        document.setFailureStage("EMBEDDING");
        document.setFailureMessage("Embedding 服务返回 HTTP 404");
        when(documentMapper.selectOne(any())).thenReturn(document);
        when(taskMapper.selectCount(any())).thenReturn(1L);
        when(chunkMapper.selectCount(any())).thenReturn(2L);

        documentService.retry(10L, 100L);

        verify(chunkMapper, never()).delete(any());
        assertEquals("PROCESSING", document.getStatus());
        assertEquals("COMPLETED", document.getParseStatus());
        assertEquals("PENDING", document.getEmbeddingStatus());
        assertEquals(70, document.getProcessProgress());
        assertNull(document.getFailureStage());
        assertNull(document.getFailureMessage());
        ArgumentCaptor<DocumentVectorizationEvent> eventCaptor =
                ArgumentCaptor.forClass(DocumentVectorizationEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals(100L, eventCaptor.getValue().documentId());
    }

    private KnowledgeDocument document(String status) {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(100L);
        document.setTenantId(10L);
        document.setKnowledgeBaseId(20L);
        document.setStatus(status);
        document.setDeleted(0);
        document.setUpdatedAt(LocalDateTime.now());
        return document;
    }
}
