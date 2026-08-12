package com.hope.enterpriserag.chat.generation;

/**
 * 面向用户的安全推理轨迹步骤，仅描述业务阶段、统计结果和校验结论。
 * 不得承载系统 Prompt、模型隐式思维链或知识库正文。
 */
public record ReasoningStep(
        String id,
        String title,
        String detail,
        String status
) {
}
