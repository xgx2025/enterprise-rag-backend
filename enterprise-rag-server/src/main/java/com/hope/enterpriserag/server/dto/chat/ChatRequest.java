package com.hope.enterpriserag.server.dto.chat;

import com.hope.enterpriserag.chat.command.ChatCommand;
import com.hope.enterpriserag.common.exception.BusinessException;
import com.hope.enterpriserag.server.dto.knowledge.RetrievalStrategyRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * 问答请求对象；租户、用户、角色和安全等级只能从认证主体解析。
 */
public record ChatRequest(
        @NotBlank @Size(max = 2000) String query,
        String conversationId,
        List<String> knowledgeBaseIds,
        RetrievalStrategyRequest strategy,
        @Min(1) @Max(20) Integer topK,
        @Min(1000) @Max(30000) Integer contextMaxCharacters
) {
    /** 转换为与 Web 框架无关的 Chat 业务命令。 */
    public ChatCommand toCommand() {
        RetrievalStrategyRequest effective = strategy == null
                ? new RetrievalStrategyRequest(null, null, null) : strategy;
        if (!effective.denseEnabled() && !effective.sparseEnabled()) {
            throw new BusinessException("Dense 和 Sparse 检索不能同时关闭");
        }
        return new ChatCommand(query.trim(), parseId(conversationId, "会话 ID"), parseKnowledgeBaseIds(),
                effective.denseEnabled(), effective.sparseEnabled(), effective.rerankEnabled(),
                topK, contextMaxCharacters);
    }

    private List<Long> parseKnowledgeBaseIds() {
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        for (String value : knowledgeBaseIds) {
            Long id = parseId(value, "知识库 ID");
            if (id != null) {
                result.add(id);
            }
        }
        return List.copyOf(result);
    }

    private Long parseId(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            long id = Long.parseLong(value.trim());
            if (id <= 0) {
                throw new NumberFormatException("non-positive");
            }
            return id;
        } catch (NumberFormatException e) {
            throw new BusinessException(field + "格式无效");
        }
    }
}
