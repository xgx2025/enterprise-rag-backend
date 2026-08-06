package com.hope.enterpriserag.knowledge.controller;

import com.hope.enterpriserag.common.Result;
import com.hope.enterpriserag.common.exception.BusinessException;
import com.hope.enterpriserag.knowledge.dto.KnowledgeBaseRequest;
import com.hope.enterpriserag.knowledge.dto.KnowledgeBaseResponse;
import com.hope.enterpriserag.knowledge.dto.KnowledgeBaseStatusRequest;
import com.hope.enterpriserag.knowledge.service.KnowledgeBaseService;
import com.hope.enterpriserag.system.entity.User;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 知识库管理 REST 接口，提供租户内知识库的查询、创建、修改和启停能力。
 */
@RestController
@RequestMapping("/knowledge-bases")
public class KnowledgeBaseController {
    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    /** 查询当前租户的知识库，可按需包含已停用数据。 */
    @GetMapping
    public Result<List<KnowledgeBaseResponse>> list(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "false") boolean includeDisabled) {
        return Result.ok(knowledgeBaseService.list(user.getTenantId(), includeDisabled));
    }

    /** 创建当前租户下名称唯一的知识库。 */
    @PostMapping
    public Result<KnowledgeBaseResponse> create(@AuthenticationPrincipal User user,
                                                @Valid @RequestBody KnowledgeBaseRequest request) {
        return Result.ok(knowledgeBaseService.create(user.getTenantId(), user.getId(), request));
    }

    /** 更新指定知识库的基础信息和默认安全等级。 */
    @PutMapping("/{id}")
    public Result<KnowledgeBaseResponse> update(@AuthenticationPrincipal User user,
                                                @PathVariable String id,
                                                @Valid @RequestBody KnowledgeBaseRequest request) {
        return Result.ok(knowledgeBaseService.update(user.getTenantId(), parseId(id), request));
    }

    /** 启用或停用知识库；存在生效文档时禁止停用。 */
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@AuthenticationPrincipal User user,
                                     @PathVariable String id,
                                     @Valid @RequestBody KnowledgeBaseStatusRequest request) {
        knowledgeBaseService.updateStatus(user.getTenantId(), parseId(id), request.status());
        return Result.ok();
    }

    /** 将知识库设为停用状态，不物理删除数据。 */
    @DeleteMapping("/{id}")
    public Result<Void> disable(@AuthenticationPrincipal User user, @PathVariable String id) {
        knowledgeBaseService.updateStatus(user.getTenantId(), parseId(id), "DISABLED");
        return Result.ok();
    }

    private Long parseId(String id) {
        try {
            return Long.valueOf(id);
        } catch (NumberFormatException e) {
            throw new BusinessException("知识库 ID 格式错误");
        }
    }
}
