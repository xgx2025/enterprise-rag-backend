package com.hope.enterpriserag.knowledge.controller;

import com.hope.enterpriserag.common.Result;
import com.hope.enterpriserag.common.exception.BusinessException;
import com.hope.enterpriserag.knowledge.dto.DocumentChunkResponse;
import com.hope.enterpriserag.knowledge.dto.DocumentResponse;
import com.hope.enterpriserag.knowledge.dto.DocumentStatusRequest;
import com.hope.enterpriserag.knowledge.dto.DocumentUploadRequest;
import com.hope.enterpriserag.knowledge.dto.ObjectAccessResponse;
import com.hope.enterpriserag.knowledge.dto.PaginatedResult;
import com.hope.enterpriserag.knowledge.service.DocumentService;
import com.hope.enterpriserag.system.entity.User;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 文档管理 REST 接口，提供文档上传、查询、发布、失效、归档、重试及原文预览能力。
 * 所有操作均以当前认证用户所属租户为数据边界。
 */
@RestController
@RequestMapping("/documents")
public class DocumentController {
    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    /** 按状态、部门、知识库和关键词分页查询当前租户文档。 */
    @GetMapping
    public Result<PaginatedResult<DocumentResponse>> list(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String knowledgeBaseId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(documentService.list(user.getTenantId(), status, department,
                parseOptionalId(knowledgeBaseId), keyword, page, pageSize));
    }

    /** 查询当前租户内的单个文档详情。 */
    @GetMapping("/{id}")
    public Result<DocumentResponse> get(@AuthenticationPrincipal User user, @PathVariable String id) {
        return Result.ok(documentService.get(user.getTenantId(), parseId(id)));
    }

    /** 上传原文件和治理元数据，并在事务提交后异步启动解析任务。 */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<DocumentResponse> upload(
            @AuthenticationPrincipal User user,
            @Valid @ModelAttribute DocumentUploadRequest request) {
        return Result.ok(documentService.upload(
                user.getTenantId(), user.getId(), request.file(), request.toCommand()));
    }

    /** 发布文档或将已发布文档设为失效。 */
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@AuthenticationPrincipal User user,
                                     @PathVariable String id,
                                     @Valid @RequestBody DocumentStatusRequest request) {
        documentService.updateStatus(user.getTenantId(), parseId(id), request.status());
        return Result.ok();
    }

    /** 重新提交失败文档的解析与分块任务。 */
    @PostMapping("/{id}/retry")
    public Result<Void> retry(@AuthenticationPrincipal User user, @PathVariable String id) {
        documentService.retry(user.getTenantId(), parseId(id));
        return Result.ok();
    }

    /** 逻辑归档非生效文档，原始 OSS 文件暂时保留。 */
    @DeleteMapping("/{id}")
    public Result<Void> archive(@AuthenticationPrincipal User user, @PathVariable String id) {
        documentService.archive(user.getTenantId(), parseId(id));
        return Result.ok();
    }

    /** 查询文档的父子分块详情。 */
    @GetMapping("/{id}/chunks")
    public Result<List<DocumentChunkResponse>> chunks(@AuthenticationPrincipal User user,
                                                      @PathVariable String id) {
        return Result.ok(documentService.chunks(user.getTenantId(), parseId(id)));
    }

    /** 生成私有 OSS 原文件的短期只读预览地址。 */
    @GetMapping("/{id}/preview-url")
    public Result<ObjectAccessResponse> previewUrl(@AuthenticationPrincipal User user,
                                                   @PathVariable String id) {
        return Result.ok(documentService.previewUrl(user.getTenantId(), parseId(id)));
    }

    private Long parseOptionalId(String id) {
        return id == null || id.isBlank() ? null : parseId(id);
    }

    private Long parseId(String id) {
        try {
            return Long.valueOf(id);
        } catch (NumberFormatException e) {
            throw new BusinessException("资源 ID 格式错误");
        }
    }
}
