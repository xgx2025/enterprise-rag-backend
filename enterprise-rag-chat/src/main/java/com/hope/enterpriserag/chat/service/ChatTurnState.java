package com.hope.enterpriserag.chat.service;

import com.hope.enterpriserag.chat.command.ChatCommand;

/**
 * 一次回答已落库的会话、用户消息和助手占位消息标识。
 */
public record ChatTurnState(
        Long conversationId,
        Long userMessageId,
        Long assistantMessageId,
        ChatCommand command
) {
}
