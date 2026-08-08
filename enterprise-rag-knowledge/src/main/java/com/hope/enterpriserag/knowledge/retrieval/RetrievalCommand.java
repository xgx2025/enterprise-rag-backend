package com.hope.enterpriserag.knowledge.retrieval;

import java.util.List;

/**
 * 与 Web 框架无关的检索命令。
 * 空知识库列表表示检索当前租户全部可用知识库；各策略开关默认由 Web 适配层补齐。
 */
public record RetrievalCommand(
        String query,
        List<Long> knowledgeBaseIds,
        boolean denseEnabled,
        boolean sparseEnabled,
        boolean rerankEnabled,
        Integer resultLimit,
        Integer contextMaxCharacters
) {
}
