package com.hope.enterpriserag.chat.model;

/**
 * 回答模型契约，输入已经过权限过滤和上下文预算控制的 Prompt。
 */
public interface ChatModel {
    /** 调用模型生成完整回答。 */
    ChatModelResult generate(ChatModelPrompt prompt);

    /** 返回当前模型 ID，用于审计 Trace。 */
    String modelName();
}
