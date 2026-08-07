package com.hope.enterpriserag.knowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hope.enterpriserag.knowledge.embedding.EmbeddingService;
import com.hope.enterpriserag.knowledge.entity.DocumentChunk;
import com.hope.enterpriserag.knowledge.entity.KnowledgeDocument;
import com.hope.enterpriserag.knowledge.event.DocumentVectorMetadataEvent;
import com.hope.enterpriserag.knowledge.mapper.DocumentChunkMapper;
import com.hope.enterpriserag.knowledge.mapper.KnowledgeDocumentMapper;
import com.hope.enterpriserag.knowledge.vector.VectorMetadata;
import com.hope.enterpriserag.knowledge.vector.VectorStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * 文档发布、失效或归档后同步 Milvus 过滤元数据，避免向量状态与 MySQL 生命周期不一致。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "rag.vectorization", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class DocumentVectorMetadataListener {
    private final KnowledgeDocumentMapper documentMapper;
    private final DocumentChunkMapper chunkMapper;
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;

    /** 在数据库状态提交后更新或删除对应文档的 Milvus 记录。 */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void process(DocumentVectorMetadataEvent event) {
        try {
            vectorStore.ensureReady(embeddingService.dimensions());
            if (event.action() == DocumentVectorMetadataEvent.Action.DELETE) {
                vectorStore.deleteDocument(event.tenantId(), event.documentId());
                log.info("已删除归档文档的Milvus向量: tenantId={}, documentId={}",
                        event.tenantId(), event.documentId());
                return;
            }

            KnowledgeDocument document = documentMapper.selectById(event.documentId());
            if (document == null || !event.tenantId().equals(document.getTenantId()) || document.getDeleted() == 1) {
                log.warn("跳过Milvus元数据同步-文档不存在、已归档或租户不一致: tenantId={}, documentId={}",
                        event.tenantId(), event.documentId());
                return;
            }
            List<DocumentChunk> chunks = chunkMapper.selectList(new LambdaQueryWrapper<DocumentChunk>()
                    .eq(DocumentChunk::getTenantId, event.tenantId())
                    .eq(DocumentChunk::getDocumentId, event.documentId())
                    .isNotNull(DocumentChunk::getParentChunkId)
                    .eq(DocumentChunk::getEmbeddingStatus, "COMPLETED"));
            List<VectorMetadata> metadata = chunks.stream()
                    .map(chunk -> new VectorMetadata(chunk.getId(), document.getStatus(),
                            document.getEffectiveFrom(), document.getEffectiveTo()))
                    .toList();
            vectorStore.updateMetadata(metadata);
            log.info("Milvus文档元数据同步完成: tenantId={}, documentId={}, status={}, chunks={}",
                    event.tenantId(), event.documentId(), document.getStatus(), metadata.size());
        } catch (Exception e) {
            log.error("Milvus文档元数据同步失败: tenantId={}, documentId={}, action={}",
                    event.tenantId(), event.documentId(), event.action(), e);
        }
    }
}
