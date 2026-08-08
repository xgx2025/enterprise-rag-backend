package com.hope.enterpriserag.server.dto.knowledge;

import com.hope.enterpriserag.common.exception.BusinessException;
import com.hope.enterpriserag.knowledge.retrieval.RetrievalCommand;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * 检索 HTTP 请求。
 * 知识库 ID 只用于缩小服务端已授权范围，租户、角色和安全等级不接受客户端传入。
 */
public record RetrievalRequest(
        @NotBlank @Size(max = 2000) String query,
        @Size(max = 50) List<String> knowledgeBaseIds,
        RetrievalStrategyRequest strategy,
        @Min(1) @Max(20) Integer topK,
        @Min(1000) @Max(30000) Integer contextMaxCharacters
) {
    /** 转换为与 Web 框架无关的知识业务命令。 */
    public RetrievalCommand toCommand() {
        RetrievalStrategyRequest effectiveStrategy = strategy == null
                ? new RetrievalStrategyRequest(null, null, null) : strategy;
        return new RetrievalCommand(query.trim(), parseKnowledgeBaseIds(),
                effectiveStrategy.denseEnabled(), effectiveStrategy.sparseEnabled(),
                effectiveStrategy.rerankEnabled(), topK, contextMaxCharacters);
    }

    private List<Long> parseKnowledgeBaseIds() {
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (String value : knowledgeBaseIds) {
            try {
                if (value == null || value.isBlank()) {
                    throw new NumberFormatException();
                }
                ids.add(Long.valueOf(value));
            } catch (NumberFormatException e) {
                throw new BusinessException("知识库 ID 格式错误");
            }
        }
        return List.copyOf(ids);
    }
}
