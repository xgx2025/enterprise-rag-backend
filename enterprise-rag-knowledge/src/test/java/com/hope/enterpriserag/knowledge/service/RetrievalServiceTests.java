package com.hope.enterpriserag.knowledge.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.hope.enterpriserag.knowledge.config.RetrievalProperties;
import com.hope.enterpriserag.knowledge.embedding.EmbeddingService;
import com.hope.enterpriserag.knowledge.entity.DocumentChunk;
import com.hope.enterpriserag.knowledge.entity.KnowledgeBase;
import com.hope.enterpriserag.knowledge.entity.KnowledgeDocument;
import com.hope.enterpriserag.knowledge.mapper.DocumentChunkMapper;
import com.hope.enterpriserag.knowledge.mapper.KnowledgeBaseMapper;
import com.hope.enterpriserag.knowledge.mapper.KnowledgeDocumentMapper;
import com.hope.enterpriserag.knowledge.retrieval.DefaultContextAssembler;
import com.hope.enterpriserag.knowledge.retrieval.HeuristicReranker;
import com.hope.enterpriserag.knowledge.retrieval.RetrievalAccessContext;
import com.hope.enterpriserag.knowledge.retrieval.RetrievalCommand;
import com.hope.enterpriserag.knowledge.vector.VectorSearchHit;
import com.hope.enterpriserag.knowledge.vector.VectorSearchRequest;
import com.hope.enterpriserag.knowledge.vector.VectorStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RetrievalServiceTests {
    private KnowledgeBaseMapper knowledgeBaseMapper;
    private KnowledgeDocumentMapper documentMapper;
    private DocumentChunkMapper chunkMapper;
    private EmbeddingService embeddingService;
    private VectorStore vectorStore;
    private RetrievalService service;

    @BeforeEach
    void setUp() {
        initializeTableInfo(KnowledgeBase.class);
        initializeTableInfo(KnowledgeDocument.class);
        initializeTableInfo(DocumentChunk.class);
        knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
        documentMapper = mock(KnowledgeDocumentMapper.class);
        chunkMapper = mock(DocumentChunkMapper.class);
        embeddingService = mock(EmbeddingService.class);
        vectorStore = mock(VectorStore.class);
        RetrievalProperties properties = new RetrievalProperties();
        properties.setDenseTopK(10);
        properties.setSparseTopK(10);
        properties.setFusionTopK(10);
        properties.setRerankTopK(5);
        properties.setContextMaxCharacters(5000);
        service = new RetrievalService(knowledgeBaseMapper, documentMapper, chunkMapper,
                embeddingService, vectorStore, new HeuristicReranker(),
                new DefaultContextAssembler(), properties);
    }

    private void initializeTableInfo(Class<?> entityType) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        assistant.setCurrentNamespace(entityType.getName());
        TableInfoHelper.initTableInfo(assistant, entityType);
    }

    @Test
    void retrievesFiltersBacktracksReranksAndBuildsContext() {
        KnowledgeDocument document = document("[\"USER\"]");
        DocumentChunk child = child();
        DocumentChunk parent = parent();
        when(knowledgeBaseMapper.selectList(any())).thenReturn(List.of(knowledgeBase()));
        when(documentMapper.selectList(any())).thenReturn(List.of(document));
        when(embeddingService.dimensions()).thenReturn(3);
        when(embeddingService.embed(any())).thenReturn(List.of(List.of(0.1F, 0.2F, 0.3F)));
        when(vectorStore.search(any())).thenReturn(List.of(new VectorSearchHit(1L, 100L, 1000L, 1, 0.88)));
        when(chunkMapper.selectList(any())).thenReturn(List.of(child), List.of(child), List.of(parent));

        var response = service.retrieve(access(), command());

        assertEquals(1, response.denseResults().size());
        assertEquals(1, response.sparseResults().size());
        assertEquals(1, response.rrfResults().size());
        assertEquals(1, response.rerankResults().size());
        assertEquals(1, response.sources().size());
        assertTrue(response.finalContext().contains("深圳住宿标准为每人每天600元"));
        assertTrue(response.timing().containsKey("parent.backtrack"));
        ArgumentCaptor<VectorSearchRequest> requestCaptor = ArgumentCaptor.forClass(VectorSearchRequest.class);
        verify(vectorStore).search(requestCaptor.capture());
        assertEquals(10L, requestCaptor.getValue().tenantId());
        assertEquals(List.of(20L), requestCaptor.getValue().knowledgeBaseIds());
        assertEquals(1, requestCaptor.getValue().maximumSecurityLevel());
    }

    @Test
    void rejectsRoleRestrictedDocumentBeforeLoadingAnyContent() {
        when(knowledgeBaseMapper.selectList(any())).thenReturn(List.of(knowledgeBase()));
        when(documentMapper.selectList(any())).thenReturn(List.of(document("[\"HR\"]")));

        var response = service.retrieve(access(), command());

        assertTrue(response.finalContext().isEmpty());
        assertTrue(response.sources().isEmpty());
        verifyNoInteractions(embeddingService, vectorStore, chunkMapper);
    }

    private RetrievalAccessContext access() {
        return new RetrievalAccessContext(10L, 99L, Set.of("ROLE_USER", "USER"), 1);
    }

    private RetrievalCommand command() {
        return new RetrievalCommand("深圳住宿标准", List.of(20L), true, true, true, 5, 5000);
    }

    private KnowledgeBase knowledgeBase() {
        KnowledgeBase value = new KnowledgeBase();
        value.setId(20L);
        value.setTenantId(10L);
        value.setStatus("ACTIVE");
        value.setSecurityLevel(1);
        return value;
    }

    private KnowledgeDocument document(String allowedRoles) {
        KnowledgeDocument value = new KnowledgeDocument();
        value.setId(100L);
        value.setTenantId(10L);
        value.setKnowledgeBaseId(20L);
        value.setTitle("差旅管理制度");
        value.setVersion("V3.0");
        value.setStatus("ACTIVE");
        value.setSecurityLevel(1);
        value.setAuthorityLevel(3);
        value.setAllowedRoles(allowedRoles);
        value.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        value.setEmbeddingStatus("COMPLETED");
        value.setDeleted(0);
        return value;
    }

    private DocumentChunk child() {
        DocumentChunk value = new DocumentChunk();
        value.setId(1L);
        value.setTenantId(10L);
        value.setDocumentId(100L);
        value.setParentChunkId(1000L);
        value.setChunkIndex(1);
        value.setContent("深圳住宿标准为600元");
        value.setSectionPath("第三章/住宿标准");
        value.setEmbeddingStatus("COMPLETED");
        return value;
    }

    private DocumentChunk parent() {
        DocumentChunk value = new DocumentChunk();
        value.setId(1000L);
        value.setTenantId(10L);
        value.setDocumentId(100L);
        value.setParentChunkId(null);
        value.setChunkIndex(0);
        value.setContent("差旅制度规定，深圳住宿标准为每人每天600元，超出部分由个人承担。");
        value.setSectionPath("第三章/住宿标准");
        value.setEmbeddingStatus("PENDING");
        return value;
    }
}
