package com.hope.enterpriserag.knowledge.command;

import java.time.LocalDate;
import java.util.List;

/**
 * 文档上传业务参数，由 multipart 请求字段转换而来，不包含文件二进制内容。
 *
 * @param title              文档业务标题
 * @param knowledgeBaseId    目标知识库 ID
 * @param department         所属部门
 * @param securityLevel      安全等级，范围为 1 至 3
 * @param version            业务版本号
 * @param effectiveFrom      生效日期
 * @param effectiveTo        失效日期；长期有效时可为空
 * @param allowedRoles       允许检索文档的角色编码
 * @param authorityLevel     内容权威等级，范围为 1 至 3
 * @param replacesDocumentId 被当前上传版本替代的旧文档 ID
 */
public record DocumentUploadCommand(
        String title,
        Long knowledgeBaseId,
        String department,
        Integer securityLevel,
        String version,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        List<String> allowedRoles,
        Integer authorityLevel,
        Long replacesDocumentId
) {
}
