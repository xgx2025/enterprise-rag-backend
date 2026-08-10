package com.hope.enterpriserag.chat.model;

/**
 * Chat 模型 Prompt，系统指令与用户输入分离，避免业务规则与检索正文混写。
 */
public record ChatModelPrompt(String systemPrompt, String userPrompt) {
}
