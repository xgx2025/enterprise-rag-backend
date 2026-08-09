package com.hope.enterpriserag.knowledge.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 文档管理接口返回的文档详情，包含存储元数据、权限规则及摄取处理状态。
 *
 * @param id                 文档 ID
 * @param knowledgeBaseId    所属知识库 ID
 * @param title              业务标题
 * @param fileName           上传时的原始文件名
 * @param fileType           大写文件扩展名
 * @param fileSize           文件大小，单位为字节
 * @param contentType        文件 MIME 类型
 * @param version            业务版本号
 * @param status             生命周期状态
 * @param department         所属部门
 * @param securityLevel      安全等级，范围为 1 至 3
 * @param allowedRoles       允许检索该文档的角色编码
 * @param authorityLevel     内容权威等级，范围为 1 至 3
 * @param chunkCount         可用于检索的子块数量
 * @param parseStatus        文本解析状态
 * @param embeddingStatus    向量化状态
 * @param processProgress    摄取进度百分比，范围为 0 至 100
 * @param failureStage       最近失败阶段
 * @param failureMessage     脱敏后的最近失败原因
 * @param effectiveFrom      生效日期
 * @param effectiveTo        失效日期；长期有效时为 {@code null}
 * @param replacesDocumentId 被当前版本替代的旧文档 ID
 * @param createdBy          创建用户 ID
 * @param createdAt          创建时间
 * @param updatedAt          最后更新时间
 */
public record DocumentResponse(
        String id,
        String knowledgeBaseId,
        String title,
        String fileName,
        String fileType,
        long fileSize,
        String contentType,
        String version,
        String status,
        String department,
        int securityLevel,
        List<String> allowedRoles,
        int authorityLevel,
        int chunkCount,
        String parseStatus,
        String embeddingStatus,
        int processProgress,
        String failureStage,
        String failureMessage,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String replacesDocumentId,
        String createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
