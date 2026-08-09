package com.hope.enterpriserag.knowledge.command;

/**
 * 创建或更新知识库的应用命令，不包含 HTTP 参数绑定与校验细节。
 *
 * @param name          租户内唯一的知识库名称
 * @param description   知识库用途说明
 * @param department    归属部门
 * @param securityLevel 默认安全等级，范围为 1 至 3
 */
public record KnowledgeBaseCommand(
        String name,
        String description,
        String department,
        Integer securityLevel
) {
}
