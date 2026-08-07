package com.hope.enterpriserag.knowledge.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hope.enterpriserag.common.exception.BusinessException;
import com.hope.enterpriserag.knowledge.dto.DocumentChunkResponse;
import com.hope.enterpriserag.knowledge.dto.DocumentResponse;
import com.hope.enterpriserag.knowledge.dto.DocumentUploadCommand;
import com.hope.enterpriserag.knowledge.dto.ObjectAccessResponse;
import com.hope.enterpriserag.knowledge.dto.PaginatedResult;
import com.hope.enterpriserag.knowledge.entity.DocumentChunk;
import com.hope.enterpriserag.knowledge.entity.IngestionTask;
import com.hope.enterpriserag.knowledge.entity.KnowledgeDocument;
import com.hope.enterpriserag.knowledge.event.DocumentUploadedEvent;
import com.hope.enterpriserag.knowledge.event.DocumentVectorMetadataEvent;
import com.hope.enterpriserag.knowledge.event.DocumentVectorizationEvent;
import com.hope.enterpriserag.knowledge.mapper.DocumentChunkMapper;
import com.hope.enterpriserag.knowledge.mapper.IngestionTaskMapper;
import com.hope.enterpriserag.knowledge.mapper.KnowledgeDocumentMapper;
import com.hope.enterpriserag.knowledge.storage.ObjectStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 企业知识文档应用服务，负责租户隔离下的上传、状态流转、归档、重试、
 * 分块查询及 OSS 原文临时访问。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("pdf", "docx", "md", "txt", "html", "htm");

    private final KnowledgeDocumentMapper documentMapper;
    private final DocumentChunkMapper chunkMapper;
    private final IngestionTaskMapper taskMapper;
    private final KnowledgeBaseService knowledgeBaseService;
    private final ObjectStorageService storageService;
    private final ApplicationEventPublisher eventPublisher;

    /** 按治理属性和关键词分页查询当前租户的未归档文档。 */
    public PaginatedResult<DocumentResponse> list(Long tenantId, String status, String department,
                                                   Long knowledgeBaseId, String keyword,
                                                   long page, long pageSize) {
        long safePage = Math.max(1, page);
        long safePageSize = Math.max(1, Math.min(100, pageSize));
        LambdaQueryWrapper<KnowledgeDocument> query = new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getTenantId, tenantId)
                .eq(KnowledgeDocument::getDeleted, 0)
                .orderByDesc(KnowledgeDocument::getUpdatedAt);
        if (StringUtils.hasText(status)) {
            query.eq(KnowledgeDocument::getStatus, status.trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(department)) {
            query.eq(KnowledgeDocument::getDepartment, department.trim());
        }
        if (knowledgeBaseId != null) {
            query.eq(KnowledgeDocument::getKnowledgeBaseId, knowledgeBaseId);
        }
        if (StringUtils.hasText(keyword)) {
            String value = keyword.trim();
            query.and(wrapper -> wrapper.like(KnowledgeDocument::getTitle, value)
                    .or().like(KnowledgeDocument::getFileName, value));
        }
        Page<KnowledgeDocument> result = documentMapper.selectPage(new Page<>(safePage, safePageSize), query);
        return new PaginatedResult<>(result.getRecords().stream().map(this::toResponse).toList(),
                result.getTotal(), safePage, safePageSize);
    }

    /** 查询属于当前租户的单个文档。 */
    public DocumentResponse get(Long tenantId, Long id) {
        return toResponse(requireOwned(tenantId, id));
    }

    /**
     * 校验并上传文档至 OSS，持久化元数据后发布异步摄取事件。
     * 数据库写入失败时会尽力清理已上传的 OSS 对象。
     */
    @Transactional
    public DocumentResponse upload(Long tenantId, Long userId, MultipartFile file, DocumentUploadCommand command) {
        validateUpload(file, command);
        knowledgeBaseService.requireActive(tenantId, command.knowledgeBaseId());
        validateReplacement(tenantId, command);

        String originalName = normalizeOriginalName(file.getOriginalFilename());
        String extension = extensionOf(originalName);
        String contentHash = sha256(file);
        long duplicateCount = documentMapper.selectCount(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getTenantId, tenantId)
                .eq(KnowledgeDocument::getContentHash, contentHash)
                .eq(KnowledgeDocument::getDeleted, 0));
        if (duplicateCount > 0) {
            throw new BusinessException("相同内容的文档已存在，请勿重复上传");
        }

        Long documentId = IdUtil.getSnowflakeNextId();
        String objectKey = buildObjectKey(tenantId, command.knowledgeBaseId(), documentId, originalName);
        log.info("开始上传文档: tenantId={}, userId={}, knowledgeBaseId={}, documentId={}, fileType={}, fileSize={}",
                tenantId, userId, command.knowledgeBaseId(), documentId, extension, file.getSize());
        try (InputStream inputStream = file.getInputStream()) {
            storageService.upload(objectKey, inputStream, file.getSize(), file.getContentType());
        } catch (IOException e) {
            log.error("读取待上传文件失败: tenantId={}, documentId={}", tenantId, documentId, e);
            throw new BusinessException("读取上传文件失败");
        }

        try {
            LocalDateTime now = LocalDateTime.now();
            KnowledgeDocument document = new KnowledgeDocument();
            document.setId(documentId);
            document.setTenantId(tenantId);
            document.setKnowledgeBaseId(command.knowledgeBaseId());
            document.setTitle(command.title().trim());
            document.setFileName(originalName);
            document.setFileType(extension.toUpperCase(Locale.ROOT));
            document.setFileSize(file.getSize());
            document.setContentType(file.getContentType());
            document.setStorageProvider("ALIYUN_OSS");
            document.setBucketName(storageService.bucketName());
            document.setObjectKey(objectKey);
            document.setContentHash(contentHash);
            document.setVersion(command.version().trim());
            document.setStatus("PROCESSING");
            document.setDepartment(command.department().trim());
            document.setSecurityLevel(command.securityLevel());
            document.setAllowedRoles(writeRoles(command.allowedRoles()));
            document.setAuthorityLevel(command.authorityLevel() == null ? 1 : command.authorityLevel());
            document.setEffectiveFrom(command.effectiveFrom());
            document.setEffectiveTo(command.effectiveTo());
            document.setReplacesDocumentId(command.replacesDocumentId());
            document.setParseStatus("PENDING");
            document.setEmbeddingStatus("PENDING");
            document.setProcessProgress(0);
            document.setChunkCount(0);
            document.setCreatedBy(userId);
            document.setDeleted(0);
            document.setCreatedAt(now);
            document.setUpdatedAt(now);
            documentMapper.insert(document);

            IngestionTask task = createTask(document, 0);
            taskMapper.insert(task);
            eventPublisher.publishEvent(new DocumentUploadedEvent(documentId, task.getId()));
            log.info("文档上传入库并提交解析任务: tenantId={}, knowledgeBaseId={}, documentId={}, taskId={}",
                    tenantId, command.knowledgeBaseId(), documentId, task.getId());
            return toResponse(document);
        } catch (RuntimeException e) {
            log.error("文档入库失败，准备清理OSS对象: tenantId={}, documentId={}", tenantId, documentId, e);
            storageService.delete(objectKey);
            throw e;
        }
    }

    /** 发布文档或将已发布文档设为失效，并处理版本替代关系。 */
    @Transactional
    public void updateStatus(Long tenantId, Long id, String targetStatus) {
        KnowledgeDocument document = requireOwned(tenantId, id);
        String previousStatus = document.getStatus();
        String target = targetStatus.toUpperCase(Locale.ROOT);
        if ("ACTIVE".equals(target)) {
            if (!("READY".equals(document.getStatus()) || "EXPIRED".equals(document.getStatus()))) {
                throw new BusinessException("仅已处理或已失效文档可以发布");
            }
            if (!"COMPLETED".equals(document.getParseStatus())) {
                throw new BusinessException("文档尚未解析完成，不能发布");
            }
            if (!"COMPLETED".equals(document.getEmbeddingStatus())) {
                throw new BusinessException("文档尚未向量化完成，不能发布");
            }
            if (document.getReplacesDocumentId() != null) {
                KnowledgeDocument replaced = requireOwned(tenantId, document.getReplacesDocumentId());
                replaced.setStatus("EXPIRED");
                replaced.setEffectiveTo(document.getEffectiveFrom());
                replaced.setUpdatedAt(LocalDateTime.now());
                documentMapper.updateById(replaced);
                eventPublisher.publishEvent(new DocumentVectorMetadataEvent(tenantId, replaced.getId(),
                        DocumentVectorMetadataEvent.Action.SYNC));
                log.info("替代文档已自动失效: tenantId={}, documentId={}, replacedDocumentId={}",
                        tenantId, id, replaced.getId());
            }
        } else if ("EXPIRED".equals(target)) {
            if (!"ACTIVE".equals(document.getStatus())) {
                throw new BusinessException("仅已生效文档可以设为失效");
            }
        } else {
            throw new BusinessException("不支持的文档状态");
        }
        document.setStatus(target);
        document.setUpdatedAt(LocalDateTime.now());
        documentMapper.updateById(document);
        eventPublisher.publishEvent(new DocumentVectorMetadataEvent(tenantId, id,
                DocumentVectorMetadataEvent.Action.SYNC));
        log.info("文档状态更新成功: tenantId={}, documentId={}, previousStatus={}, targetStatus={}",
                tenantId, id, previousStatus, target);
    }

    /** 逻辑归档非生效文档，保留 OSS 原文件以满足审计和恢复需求。 */
    @Transactional
    public void archive(Long tenantId, Long id) {
        KnowledgeDocument document = requireOwned(tenantId, id);
        if ("ACTIVE".equals(document.getStatus())) {
            throw new BusinessException("已生效文档不能直接删除，请先设为失效");
        }
        document.setStatus("ARCHIVED");
        document.setDeleted(1);
        document.setUpdatedAt(LocalDateTime.now());
        documentMapper.updateById(document);
        eventPublisher.publishEvent(new DocumentVectorMetadataEvent(tenantId, id,
                DocumentVectorMetadataEvent.Action.DELETE));
        log.info("文档已归档，OSS原文件继续保留: tenantId={}, documentId={}", tenantId, id);
    }

    /** 根据失败阶段重新提交解析任务或仅重跑向量化，避免无意义地重复下载原文件。 */
    @Transactional
    public void retry(Long tenantId, Long id) {
        KnowledgeDocument document = requireOwnedForUpdate(tenantId, id);
        if (!"FAILED".equals(document.getStatus())) {
            throw new BusinessException("仅处理失败的文档可以重试");
        }
        int retryCount = Math.toIntExact(taskMapper.selectCount(new LambdaQueryWrapper<IngestionTask>()
                .eq(IngestionTask::getDocumentId, id)));
        boolean vectorOnly = "EMBEDDING".equals(document.getFailureStage())
                && "COMPLETED".equals(document.getParseStatus())
                && chunkMapper.selectCount(new LambdaQueryWrapper<DocumentChunk>()
                        .eq(DocumentChunk::getTenantId, tenantId)
                        .eq(DocumentChunk::getDocumentId, id)
                        .isNotNull(DocumentChunk::getParentChunkId)) > 0;

        document.setStatus("PROCESSING");
        document.setEmbeddingStatus("PENDING");
        document.setFailureStage(null);
        document.setFailureMessage(null);
        document.setUpdatedAt(LocalDateTime.now());
        if (vectorOnly) {
            document.setProcessProgress(70);
            chunkMapper.update(null, new UpdateWrapper<DocumentChunk>()
                    .eq("tenant_id", tenantId)
                    .eq("document_id", id)
                    .isNotNull("parent_chunk_id")
                    .set("embedding_status", "PENDING"));
            documentMapper.updateById(document);
            IngestionTask task = createTask(document, retryCount, "VECTORIZE", "WAITING_VECTOR", 70,
                    "EMBEDDING_PENDING");
            taskMapper.insert(task);
            eventPublisher.publishEvent(new DocumentVectorizationEvent(id, task.getId()));
            log.info("文档向量化重试任务已提交: tenantId={}, documentId={}, taskId={}, retryCount={}",
                    tenantId, id, task.getId(), retryCount);
            return;
        }

        chunkMapper.delete(new LambdaQueryWrapper<DocumentChunk>()
                .eq(DocumentChunk::getTenantId, tenantId)
                .eq(DocumentChunk::getDocumentId, id));
        document.setParseStatus("PENDING");
        document.setProcessProgress(0);
        document.setChunkCount(0);
        documentMapper.updateById(document);
        IngestionTask task = createTask(document, retryCount);
        taskMapper.insert(task);
        eventPublisher.publishEvent(new DocumentUploadedEvent(id, task.getId()));
        log.info("文档解析重试任务已提交: tenantId={}, documentId={}, taskId={}, retryCount={}",
                tenantId, id, task.getId(), retryCount);
    }

    /** 查询当前租户文档的全部父子分块。 */
    public List<DocumentChunkResponse> chunks(Long tenantId, Long documentId) {
        requireOwned(tenantId, documentId);
        return chunkMapper.selectList(new LambdaQueryWrapper<DocumentChunk>()
                        .eq(DocumentChunk::getTenantId, tenantId)
                        .eq(DocumentChunk::getDocumentId, documentId)
                        .orderByAsc(DocumentChunk::getChunkIndex))
                .stream().map(this::toChunkResponse).toList();
    }

    /** 生成当前租户文档原文件的短期只读访问地址。 */
    public ObjectAccessResponse previewUrl(Long tenantId, Long id) {
        KnowledgeDocument document = requireOwned(tenantId, id);
        var expiration = storageService.readUrlExpiration();
        log.info("生成文档临时预览地址: tenantId={}, documentId={}, expirationMinutes={}",
                tenantId, id, expiration.toMinutes());
        return new ObjectAccessResponse(storageService.generateReadUrl(document.getObjectKey(), expiration),
                Instant.now().plus(expiration));
    }

    /**
     * 获取属于当前租户且未归档的文档，防止跨租户访问。
     *
     * @throws BusinessException 文档不存在、已归档或不属于当前租户
     */
    public KnowledgeDocument requireOwned(Long tenantId, Long id) {
        KnowledgeDocument document = documentMapper.selectOne(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getId, id)
                .eq(KnowledgeDocument::getTenantId, tenantId)
                .eq(KnowledgeDocument::getDeleted, 0));
        if (document == null) {
            log.warn("文档访问失败-资源不存在、已归档或不属于当前租户: tenantId={}, documentId={}", tenantId, id);
            throw new BusinessException(404, "文档不存在");
        }
        return document;
    }

    /** 在写事务中锁定当前租户文档，防止并发重试创建多个摄取任务。 */
    private KnowledgeDocument requireOwnedForUpdate(Long tenantId, Long id) {
        KnowledgeDocument document = documentMapper.selectOne(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getId, id)
                .eq(KnowledgeDocument::getTenantId, tenantId)
                .eq(KnowledgeDocument::getDeleted, 0)
                .last("FOR UPDATE"));
        if (document == null) {
            log.warn("文档重试失败-资源不存在、已归档或不属于当前租户: tenantId={}, documentId={}", tenantId, id);
            throw new BusinessException(404, "文档不存在");
        }
        return document;
    }

    private IngestionTask createTask(KnowledgeDocument document, int retryCount) {
        return createTask(document, retryCount, "PARSE_AND_CHUNK", "PENDING", 0, "WAITING");
    }

    private IngestionTask createTask(KnowledgeDocument document, int retryCount, String taskType,
                                     String status, int progress, String stage) {
        LocalDateTime now = LocalDateTime.now();
        IngestionTask task = new IngestionTask();
        task.setId(IdUtil.getSnowflakeNextId());
        task.setTenantId(document.getTenantId());
        task.setDocumentId(document.getId());
        task.setTaskType(taskType);
        task.setStatus(status);
        task.setProgress(progress);
        task.setCurrentStage(stage);
        task.setRetryCount(retryCount);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        return task;
    }

    private void validateUpload(MultipartFile file, DocumentUploadCommand command) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("文件大小不能超过 50MB");
        }
        String extension = extensionOf(normalizeOriginalName(file.getOriginalFilename()));
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new BusinessException("仅支持 PDF、DOCX、Markdown、TXT 和 HTML 文件");
        }
        if (command.title() == null || command.title().isBlank() || command.title().length() > 256) {
            throw new BusinessException("文档标题不能为空且不能超过 256 个字符");
        }
        if (command.knowledgeBaseId() == null) {
            throw new BusinessException("请选择所属知识库");
        }
        if (command.department() == null || command.department().isBlank()) {
            throw new BusinessException("请选择所属部门");
        }
        if (command.version() == null || command.version().isBlank() || command.version().length() > 64) {
            throw new BusinessException("版本号不能为空且不能超过 64 个字符");
        }
        if (command.securityLevel() == null || command.securityLevel() < 1 || command.securityLevel() > 3) {
            throw new BusinessException("安全等级必须为 1 到 3");
        }
        if (command.authorityLevel() != null && (command.authorityLevel() < 1 || command.authorityLevel() > 3)) {
            throw new BusinessException("权威等级必须为 1 到 3");
        }
        if (command.effectiveFrom() != null && command.effectiveTo() != null
                && command.effectiveTo().isBefore(command.effectiveFrom())) {
            throw new BusinessException("失效日期不能早于生效日期");
        }
    }

    private void validateReplacement(Long tenantId, DocumentUploadCommand command) {
        if (command.replacesDocumentId() == null) {
            return;
        }
        KnowledgeDocument replaced = requireOwned(tenantId, command.replacesDocumentId());
        if (!replaced.getKnowledgeBaseId().equals(command.knowledgeBaseId())) {
            throw new BusinessException("被替代文档必须属于同一知识库");
        }
    }

    private String sha256(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            throw new BusinessException("计算文件摘要失败");
        }
    }

    private String buildObjectKey(Long tenantId, Long knowledgeBaseId, Long documentId, String fileName) {
        String safeName = fileName.replaceAll("[^\\p{L}\\p{N}._-]", "_");
        return String.format("%s/%s/%s/%s/%s", storageService.objectPrefix(), tenantId,
                knowledgeBaseId, documentId, safeName);
    }

    private String normalizeOriginalName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new BusinessException("文件名不能为空");
        }
        String normalized = name.replace('\\', '/');
        normalized = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (normalized.isBlank() || normalized.length() > 256) {
            throw new BusinessException("文件名无效或过长");
        }
        return normalized;
    }

    private String extensionOf(String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private String writeRoles(List<String> roles) {
        List<String> normalized = roles == null ? List.of() : roles.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .map(value -> value.toUpperCase(Locale.ROOT))
                .filter(value -> value.matches("[A-Z0-9_]{1,64}"))
                .distinct()
                .toList();
        return normalized.stream().map(value -> "\"" + value + "\"")
                .collect(Collectors.joining(",", "[", "]"));
    }

    private List<String> readRoles(String rolesJson) {
        if (!StringUtils.hasText(rolesJson)) {
            return List.of();
        }
        String content = rolesJson.trim();
        if (content.length() < 2 || content.charAt(0) != '[' || content.charAt(content.length() - 1) != ']') {
            return List.of();
        }
        String body = content.substring(1, content.length() - 1).trim();
        if (body.isEmpty()) {
            return List.of();
        }
        return List.of(body.split(",")).stream()
                .map(String::trim)
                .map(value -> value.replaceAll("^\"|\"$", ""))
                .filter(value -> value.matches("[A-Z0-9_]{1,64}"))
                .toList();
    }

    private DocumentResponse toResponse(KnowledgeDocument document) {
        return new DocumentResponse(
                String.valueOf(document.getId()), String.valueOf(document.getKnowledgeBaseId()),
                document.getTitle(), document.getFileName(), document.getFileType(), document.getFileSize(),
                document.getContentType(), document.getVersion(), document.getStatus(), document.getDepartment(),
                document.getSecurityLevel(), readRoles(document.getAllowedRoles()), document.getAuthorityLevel(),
                document.getChunkCount(), document.getParseStatus(), document.getEmbeddingStatus(),
                document.getProcessProgress(), document.getFailureStage(), document.getFailureMessage(),
                document.getEffectiveFrom(), document.getEffectiveTo(),
                document.getReplacesDocumentId() == null ? null : String.valueOf(document.getReplacesDocumentId()),
                String.valueOf(document.getCreatedBy()), document.getCreatedAt(), document.getUpdatedAt());
    }

    private DocumentChunkResponse toChunkResponse(DocumentChunk chunk) {
        return new DocumentChunkResponse(
                String.valueOf(chunk.getId()), String.valueOf(chunk.getDocumentId()),
                chunk.getParentChunkId() == null ? null : String.valueOf(chunk.getParentChunkId()),
                chunk.getChunkIndex(), chunk.getContent(), chunk.getSectionPath(), chunk.getPageNumber(),
                chunk.getTokenCount(), chunk.getEmbeddingStatus(), chunk.getMetadataJson());
    }
}
