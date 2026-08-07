package com.hope.enterpriserag.server.dto.knowledge;

import com.hope.enterpriserag.knowledge.command.KnowledgeBaseCommand;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建或更新知识库的请求参数。
 *
 * @param name          租户内唯一的知识库名称，最长 128 个字符
 * @param description   知识库用途说明，最长 500 个字符
 * @param department    归属部门，最长 64 个字符
 * @param securityLevel 默认安全等级，范围为 1 至 3
 */
public record KnowledgeBaseRequest(
        @NotBlank @Size(max = 128) String name,
        @Size(max = 500) String description,
        @Size(max = 64) String department,
        @Min(1) @Max(3) Integer securityLevel
) {
    /** 将 Web 请求转换为知识库应用命令。 */
    public KnowledgeBaseCommand toCommand() {
        return new KnowledgeBaseCommand(name, description, department, securityLevel);
    }
}
