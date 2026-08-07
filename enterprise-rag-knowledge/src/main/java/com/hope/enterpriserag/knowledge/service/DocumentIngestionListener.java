package com.hope.enterpriserag.knowledge.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hope.enterpriserag.knowledge.entity.DocumentChunk;
import com.hope.enterpriserag.knowledge.entity.IngestionTask;
import com.hope.enterpriserag.knowledge.entity.KnowledgeDocument;
import com.hope.enterpriserag.knowledge.event.DocumentUploadedEvent;
import com.hope.enterpriserag.knowledge.event.DocumentVectorizationEvent;
import com.hope.enterpriserag.knowledge.mapper.DocumentChunkMapper;
import com.hope.enterpriserag.knowledge.mapper.IngestionTaskMapper;
import com.hope.enterpriserag.knowledge.mapper.KnowledgeDocumentMapper;
import com.hope.enterpriserag.knowledge.storage.ObjectStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.springframework.scheduling.annotation.Async;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 文档上传后的异步摄取处理器。
 * 在上传事务提交后从 OSS 读取原文件，使用 Tika 提取文本并生成父子分块；
 * 向量化由后续任务继续完成。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentIngestionListener {
    private static final int MAX_EXTRACTED_CHARACTERS = 5_000_000;
    private static final int PARENT_CHUNK_SIZE = 3_000;
    private static final int PARENT_OVERLAP = 200;
    private static final int CHILD_CHUNK_SIZE = 900;
    private static final int CHILD_OVERLAP = 100;

    private final KnowledgeDocumentMapper documentMapper;
    private final DocumentChunkMapper chunkMapper;
    private final IngestionTaskMapper taskMapper;
    private final ObjectStorageService storageService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 处理已提交的文档上传事件，并持续更新文档与任务状态。
     *
     * @param event 包含文档 ID 和摄取任务 ID 的领域事件
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void process(DocumentUploadedEvent event) {
        long startedAt = System.nanoTime();
        KnowledgeDocument document = documentMapper.selectById(event.documentId());
        IngestionTask task = taskMapper.selectById(event.taskId());
        if (document == null || task == null || document.getDeleted() == 1) {
            log.warn("跳过文档解析任务-资源不存在或已归档: documentId={}, taskId={}, documentExists={}, taskExists={}",
                    event.documentId(), event.taskId(), document != null, task != null);
            return;
        }

        try {
            log.info("文档解析任务开始: tenantId={}, documentId={}, taskId={}, retryCount={}",
                    document.getTenantId(), document.getId(), task.getId(), task.getRetryCount());
            updateTask(task, "RUNNING", 10, "DOWNLOAD", null, false);
            updateDocument(document, "PROCESSING", "PARSING", 15, null, null);

            String text;
            try (InputStream inputStream = storageService.download(document.getObjectKey())) {
                Metadata metadata = new Metadata();
                metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, document.getFileName());
                text = new Tika().parseToString(inputStream, metadata, MAX_EXTRACTED_CHARACTERS);
            }

            String normalized = normalize(text);
            if (normalized.isBlank()) {
                throw new IllegalStateException("文档未解析出可用文本，扫描版 PDF 请先进行 OCR");
            }

            updateTask(task, "RUNNING", 45, "CHUNKING", null, false);
            chunkMapper.delete(new LambdaQueryWrapper<DocumentChunk>()
                    .eq(DocumentChunk::getDocumentId, document.getId()));

            int chunkIndex = 0;
            int childCount = 0;
            List<String> parents = split(normalized, PARENT_CHUNK_SIZE, PARENT_OVERLAP);
            for (int parentIndex = 0; parentIndex < parents.size(); parentIndex++) {
                String parentContent = parents.get(parentIndex);
                Long parentId = IdUtil.getSnowflakeNextId();
                insertChunk(document, parentId, null, chunkIndex++, parentContent,
                        "正文/父块 " + (parentIndex + 1), "PARENT");

                List<String> children = split(parentContent, CHILD_CHUNK_SIZE, CHILD_OVERLAP);
                for (int childIndex = 0; childIndex < children.size(); childIndex++) {
                    insertChunk(document, IdUtil.getSnowflakeNextId(), parentId, chunkIndex++,
                            children.get(childIndex),
                            "正文/父块 " + (parentIndex + 1) + "/子块 " + (childIndex + 1), "CHILD");
                    childCount++;
                }
            }

            document.setChunkCount(childCount);
            document.setEmbeddingStatus("PENDING");
            updateDocument(document, "PROCESSING", "COMPLETED", 70, null, null);
            updateTask(task, "WAITING_VECTOR", 70, "EMBEDDING_PENDING", null, false);
            eventPublisher.publishEvent(new DocumentVectorizationEvent(document.getId(), task.getId()));
            log.info("文档解析与分块完成: tenantId={}, documentId={}, taskId={}, parentChunks={}, childChunks={}, elapsedMs={}",
                    document.getTenantId(), document.getId(), task.getId(), parents.size(), childCount,
                    elapsedMillis(startedAt));
        } catch (Exception e) {
            String message = safeMessage(e);
            updateDocument(document, "FAILED", "FAILED", 0, "PARSE_AND_CHUNK", message);
            updateTask(task, "FAILED", 0, "PARSE_AND_CHUNK", message, true);
            log.error("文档解析失败: tenantId={}, documentId={}, taskId={}, elapsedMs={}, error={}",
                    document.getTenantId(), document.getId(), task.getId(), elapsedMillis(startedAt), message, e);
        }
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private void insertChunk(KnowledgeDocument document, Long id, Long parentId, int index,
                             String content, String sectionPath, String type) {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setId(id);
        chunk.setTenantId(document.getTenantId());
        chunk.setDocumentId(document.getId());
        chunk.setParentChunkId(parentId);
        chunk.setChunkIndex(index);
        chunk.setContent(content);
        chunk.setSectionPath(sectionPath);
        chunk.setPageNumber(null);
        chunk.setTokenCount(estimateTokens(content));
        chunk.setEmbeddingStatus("PENDING");
        chunk.setMetadataJson("{\"chunkType\":\"" + type + "\"}");
        chunk.setCreatedAt(LocalDateTime.now());
        chunkMapper.insert(chunk);
    }

    private void updateDocument(KnowledgeDocument document, String status, String parseStatus,
                                int progress, String failureStage, String failureMessage) {
        document.setStatus(status);
        document.setParseStatus(parseStatus);
        document.setProcessProgress(progress);
        document.setFailureStage(failureStage);
        document.setFailureMessage(failureMessage);
        document.setUpdatedAt(LocalDateTime.now());
        documentMapper.updateById(document);
    }

    private void updateTask(IngestionTask task, String status, int progress, String stage,
                            String error, boolean finished) {
        LocalDateTime now = LocalDateTime.now();
        if (task.getStartedAt() == null) {
            task.setStartedAt(now);
        }
        task.setStatus(status);
        task.setProgress(progress);
        task.setCurrentStage(stage);
        task.setErrorMessage(error);
        task.setUpdatedAt(now);
        if (finished) {
            task.setFinishedAt(now);
        }
        taskMapper.updateById(task);
    }

    private List<String> split(String text, int maxLength, int overlap) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + maxLength);
            if (end < text.length()) {
                int boundary = findBoundary(text, start, end);
                if (boundary > start + maxLength / 2) {
                    end = boundary;
                }
            }
            String chunk = text.substring(start, end).trim();
            if (!chunk.isBlank()) {
                chunks.add(chunk);
            }
            if (end >= text.length()) {
                break;
            }
            start = Math.max(start + 1, end - overlap);
        }
        return chunks;
    }

    private int findBoundary(String text, int start, int end) {
        for (int i = end - 1; i > start; i--) {
            char c = text.charAt(i);
            if (c == '\n' || c == '。' || c == '！' || c == '？' || c == ';' || c == '；') {
                return i + 1;
            }
        }
        return end;
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\u0000", "")
                .replaceAll("[\\t\\x0B\\f\\r ]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private int estimateTokens(String content) {
        return Math.max(1, (int) Math.ceil(content.length() / 2.0));
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            message = e.getClass().getSimpleName();
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
