package com.hope.enterpriserag.chat.command;

import java.util.List;

/**
 * 与 Web 框架无关的问答命令，知识库范围只能缩小服务端可访问范围。
 */
public record ChatCommand(
        String query,
        Long conversationId,
        List<Long> knowledgeBaseIds,
        boolean denseEnabled,
        boolean sparseEnabled,
        boolean rerankEnabled,
        Integer resultLimit,
        Integer contextMaxCharacters
) {
}
