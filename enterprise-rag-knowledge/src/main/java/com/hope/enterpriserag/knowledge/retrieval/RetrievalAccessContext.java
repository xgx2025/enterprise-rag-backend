package com.hope.enterpriserag.knowledge.retrieval;

import java.util.Set;

/**
 * 从服务端认证上下文派生的检索访问边界。
 * 客户端只能缩小知识库范围，不能覆盖本对象中的租户、角色或安全等级。
 *
 * @param tenantId             当前租户 ID
 * @param userId               当前用户 ID
 * @param roles                当前用户规范化角色编码
 * @param maximumSecurityLevel 可访问的最高文档安全等级，范围为 1 至 3
 */
public record RetrievalAccessContext(
        Long tenantId,
        Long userId,
        Set<String> roles,
        int maximumSecurityLevel
) {
}
