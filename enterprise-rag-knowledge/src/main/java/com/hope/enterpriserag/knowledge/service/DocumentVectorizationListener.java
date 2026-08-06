package com.hope.enterpriserag.knowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.hope.enterpriserag.knowledge.config.VectorizationProperties;
import com.hope.enterpriserag.knowledge.embedding.EmbeddingService;
import com.hope.enterpriserag.knowledge.entity.DocumentChunk;
import com.hope.enterpriserag.knowledge.entity.IngestionTask;
import com.hope.enterpriserag.knowledge.entity.KnowledgeDocument;
import com.hope.enterpriserag.knowledge.event.DocumentVectorizationEvent;
import com.hope.enterpriserag.knowledge.mapper.DocumentChunkMapper;
import com.hope.enterpriserag.knowledge.mapper.IngestionTaskMapper;
import com.hope.enterpriserag.knowledge.mapper.KnowledgeDocumentMapper;
import com.hope.enterpriserag.knowledge.vector.VectorRecord;
import com.hope.enterpriserag.knowledge.vector.VectorStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 文档子块异步向量化处理器。
 * 仅向量化子块，父块继续保存在 MySQL 中并通过 {@code parentChunkId} 在召回后回溯。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "rag.vectorization", name = "enabled", havingValue = "true")
public class DocumentVectorizationListener {
    private static final int VECTOR_START_PROGRESS = 70;
    private static final int VECTOR_PROGRESS_SPAN = 29;

    private final KnowledgeDocumentMapper documentMapper;
    private final DocumentChunkMapper chunkMapper;
    private final IngestionTaskMapper taskMapper;
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final VectorizationProperties properties;

    public DocumentVectorizationListener(KnowledgeDocumentMapper documentMapper,
                                         DocumentChunkMapper chunkMapper,
                                         IngestionTaskMapper taskMapper,
                                         EmbeddingService embeddingService,
                                         VectorStore vectorStore,
                                         VectorizationProperties properties) {
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
        this.taskMapper = taskMapper;
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.properties = properties;
    }

    /**
     * 在分块事务提交后批量生成向量并写入 Milvus。
     * 使用文档级先删后 Upsert 策略清理重新解析产生的旧分块，保证手工重试幂等。
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void process(DocumentVectorizationEvent event) {
        long startedAt = System.nanoTime();
        KnowledgeDocument document = documentMapper.selectById(event.documentId());
        IngestionTask task = taskMapper.selectById(event.taskId());
        if (document == null || task == null || document.getDeleted() == 1
                || !document.getId().equals(task.getDocumentId())
                || !document.getTenantId().equals(task.getTenantId())) {
            log.warn("跳过文档向量化任务-资源不存在、已归档或任务归属不一致: documentId={}, taskId={}, documentExists={}, taskExists={}",
                    event.documentId(), event.taskId(), document != null, task != null);
            return;
        }

        try {
            int batchSize = properties.getBatchSize();
            if (batchSize <= 0 || batchSize > 256) {
                throw new IllegalStateException("rag.vectorization.batch-size 必须在 1 到 256 之间");
            }
            if (!claimTask(task)) {
                log.warn("跳过重复或已失效的文档向量化事件: tenantId={}, documentId={}, taskId={}, taskStatus={}",
                        document.getTenantId(), document.getId(), task.getId(), task.getStatus());
                return;
            }
            List<DocumentChunk> chunks = chunkMapper.selectList(new LambdaQueryWrapper<DocumentChunk>()
                    .eq(DocumentChunk::getTenantId, document.getTenantId())
                    .eq(DocumentChunk::getDocumentId, document.getId())
                    .isNotNull(DocumentChunk::getParentChunkId)
                    .orderByAsc(DocumentChunk::getChunkIndex));
            if (chunks.isEmpty()) {
                throw new IllegalStateException("文档没有可向量化的子块");
            }

            log.info("文档向量化任务开始: tenantId={}, knowledgeBaseId={}, documentId={}, taskId={}, chunks={}, dimensions={}",
                    document.getTenantId(), document.getKnowledgeBaseId(), document.getId(), task.getId(),
                    chunks.size(), embeddingService.dimensions());
            updateDocument(document, "PROCESSING", "RUNNING", VECTOR_START_PROGRESS, null, null);
            markChunks(document, chunks.stream().map(DocumentChunk::getId).toList(), "RUNNING");

            vectorStore.ensureReady(embeddingService.dimensions());
            vectorStore.deleteDocument(document.getTenantId(), document.getId());

            int processed = 0;
            for (int offset = 0; offset < chunks.size(); offset += batchSize) {
                List<DocumentChunk> batch = chunks.subList(offset, Math.min(offset + batchSize, chunks.size()));
                List<List<Float>> embeddings = embeddingService.embed(
                        batch.stream().map(DocumentChunk::getContent).toList());
                if (embeddings.size() != batch.size()) {
                    throw new IllegalStateException("Embedding 返回数量与分块数量不一致");
                }

                List<VectorRecord> records = new ArrayList<>(batch.size());
                for (int index = 0; index < batch.size(); index++) {
                    records.add(toVectorRecord(document, batch.get(index), embeddings.get(index)));
                }
                vectorStore.upsert(records);
                markChunks(document, batch.stream().map(DocumentChunk::getId).toList(), "COMPLETED");

                processed += batch.size();
                int progress = VECTOR_START_PROGRESS
                        + (int) Math.floor((double) processed / chunks.size() * VECTOR_PROGRESS_SPAN);
                updateTask(task, "RUNNING", progress, "MILVUS_UPSERT", null, false);
                updateDocument(document, "PROCESSING", "RUNNING", progress, null, null);
            }

            updateDocument(document, "READY", "COMPLETED", 100, null, null);
            updateTask(task, "SUCCEEDED", 100, "COMPLETED", null, true);
            log.info("文档向量化任务完成: tenantId={}, knowledgeBaseId={}, documentId={}, taskId={}, chunks={}, elapsedMs={}",
                    document.getTenantId(), document.getKnowledgeBaseId(), document.getId(), task.getId(),
                    chunks.size(), elapsedMillis(startedAt));
        } catch (Exception e) {
            String message = safeMessage(e);
            markIncompleteChunksFailed(document);
            updateDocument(document, "FAILED", "FAILED", document.getProcessProgress(), "EMBEDDING", message);
            updateTask(task, "FAILED", document.getProcessProgress(), "EMBEDDING", message, true);
            log.error("文档向量化失败: tenantId={}, knowledgeBaseId={}, documentId={}, taskId={}, elapsedMs={}, error={}",
                    document.getTenantId(), document.getKnowledgeBaseId(), document.getId(), task.getId(),
                    elapsedMillis(startedAt), message, e);
        }
    }

    private VectorRecord toVectorRecord(KnowledgeDocument document, DocumentChunk chunk, List<Float> embedding) {
        return new VectorRecord(
                chunk.getId(), document.getTenantId(), document.getKnowledgeBaseId(), document.getId(),
                chunk.getParentChunkId(), chunk.getChunkIndex(), document.getVersion(), "READY",
                document.getDepartment(), document.getSecurityLevel(), document.getAuthorityLevel(),
                document.getEffectiveFrom(), document.getEffectiveTo(), document.getAllowedRoles(),
                chunk.getSectionPath(), chunk.getPageNumber(), embedding);
    }

    private void markChunks(KnowledgeDocument document, List<Long> chunkIds, String status) {
        if (chunkIds.isEmpty()) {
            return;
        }
        chunkMapper.update(null, new UpdateWrapper<DocumentChunk>()
                .eq("tenant_id", document.getTenantId())
                .eq("document_id", document.getId())
                .in("id", chunkIds)
                .set("embedding_status", status));
    }

    private boolean claimTask(IngestionTask task) {
        LocalDateTime now = LocalDateTime.now();
        UpdateWrapper<IngestionTask> update = new UpdateWrapper<IngestionTask>()
                .eq("id", task.getId())
                .eq("tenant_id", task.getTenantId())
                .eq("document_id", task.getDocumentId())
                .eq("status", "WAITING_VECTOR")
                .set("status", "RUNNING")
                .set("progress", VECTOR_START_PROGRESS)
                .set("current_stage", "EMBEDDING")
                .set("error_message", null)
                .set("finished_at", null)
                .set("updated_at", now);
        if (task.getStartedAt() == null) {
            update.set("started_at", now);
        }
        int updated = taskMapper.update(null, update);
        if (updated == 1) {
            task.setStatus("RUNNING");
            task.setProgress(VECTOR_START_PROGRESS);
            task.setCurrentStage("EMBEDDING");
            task.setErrorMessage(null);
            task.setFinishedAt(null);
            task.setUpdatedAt(now);
            if (task.getStartedAt() == null) {
                task.setStartedAt(now);
            }
            return true;
        }
        return false;
    }

    private void markIncompleteChunksFailed(KnowledgeDocument document) {
        chunkMapper.update(null, new UpdateWrapper<DocumentChunk>()
                .eq("tenant_id", document.getTenantId())
                .eq("document_id", document.getId())
                .isNotNull("parent_chunk_id")
                .ne("embedding_status", "COMPLETED")
                .set("embedding_status", "FAILED"));
    }

    private void updateDocument(KnowledgeDocument document, String status, String embeddingStatus,
                                Integer progress, String failureStage, String failureMessage) {
        document.setStatus(status);
        document.setEmbeddingStatus(embeddingStatus);
        document.setProcessProgress(progress == null ? VECTOR_START_PROGRESS : progress);
        document.setFailureStage(failureStage);
        document.setFailureMessage(failureMessage);
        document.setUpdatedAt(LocalDateTime.now());
        documentMapper.updateById(document);
    }

    private void updateTask(IngestionTask task, String status, Integer progress, String stage,
                            String error, boolean finished) {
        LocalDateTime now = LocalDateTime.now();
        if (task.getStartedAt() == null) {
            task.setStartedAt(now);
        }
        task.setStatus(status);
        task.setProgress(progress == null ? VECTOR_START_PROGRESS : progress);
        task.setCurrentStage(stage);
        task.setErrorMessage(error);
        task.setUpdatedAt(now);
        task.setFinishedAt(finished ? now : null);
        taskMapper.updateById(task);
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            message = e.getClass().getSimpleName();
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
