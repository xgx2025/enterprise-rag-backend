package com.hope.enterpriserag.server.controller.knowledge;

import com.hope.enterpriserag.common.Result;
import com.hope.enterpriserag.common.exception.BusinessException;
import com.hope.enterpriserag.knowledge.dto.KnowledgeBaseResponse;
import com.hope.enterpriserag.knowledge.service.KnowledgeBaseService;
import com.hope.enterpriserag.server.dto.knowledge.KnowledgeBaseRequest;
import com.hope.enterpriserag.server.dto.knowledge.KnowledgeBaseStatusRequest;
import com.hope.enterpriserag.server.support.ChatAccessContextFactory;
import com.hope.enterpriserag.system.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequiredArgsConstructor
public class KnowledgeBaseController {
    private final KnowledgeBaseService knowledgeBaseService;
    private final ChatAccessContextFactory accessContextFactory;

    /** 查询当前租户的知识库，可按需包含已停用数据。 */
    @GetMapping
    public Result<List<KnowledgeBaseResponse>> list(
            @AuthenticationPrincipal User user,
            Authentication authentication,
            @RequestParam(defaultValue = "false") boolean includeDisabled) {
        return Result.ok(knowledgeBaseService.listAccessible(
                accessContextFactory.create(user, authentication), includeDisabled));
    }

    /** 创建当前租户下名称唯一的知识库。 */
    @PostMapping
    @PreAuthorize("hasRole('KB_ADMIN')")
    public Result<KnowledgeBaseResponse> create(@AuthenticationPrincipal User user,
                                                Authentication authentication,
                                                @Valid @RequestBody KnowledgeBaseRequest request) {
        return Result.ok(knowledgeBaseService.create(
                accessContextFactory.create(user, authentication), request.toCommand()));
    }

    /** 更新指定知识库的基础信息和默认安全等级。 */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('KB_ADMIN')")
    public Result<KnowledgeBaseResponse> update(@AuthenticationPrincipal User user,
                                                Authentication authentication,
                                                @PathVariable String id,
                                                @Valid @RequestBody KnowledgeBaseRequest request) {
        return Result.ok(knowledgeBaseService.update(accessContextFactory.create(user, authentication),
                parseId(id), request.toCommand()));
    }

    /** 启用或停用知识库；存在生效文档时禁止停用。 */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('KB_ADMIN')")
    public Result<Void> updateStatus(@AuthenticationPrincipal User user,
                                     Authentication authentication,
                                     @PathVariable String id,
                                     @Valid @RequestBody KnowledgeBaseStatusRequest request) {
        knowledgeBaseService.updateStatus(accessContextFactory.create(user, authentication),
                parseId(id), request.status());
        return Result.ok();
    }

    /** 将知识库设为停用状态，不物理删除数据。 */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('KB_ADMIN')")
    public Result<Void> disable(@AuthenticationPrincipal User user, Authentication authentication,
                                @PathVariable String id) {
        knowledgeBaseService.updateStatus(accessContextFactory.create(user, authentication),
                parseId(id), "DISABLED");
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
