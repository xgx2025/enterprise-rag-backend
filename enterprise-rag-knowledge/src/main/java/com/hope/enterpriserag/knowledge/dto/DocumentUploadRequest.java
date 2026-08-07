package com.hope.enterpriserag.knowledge.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

/**
 * 文档上传 multipart 请求，统一承载原文件和文档治理元数据。
 * 缺省安全等级、版本号和权威等级在绑定阶段归一化，跨字段及业务归属规则由服务层校验。
 *
 * @param file               待上传的原文件，文件内容及大小限制由服务层校验
 * @param title              文档业务标题，最长 256 个字符
 * @param knowledgeBaseId    目标知识库 ID
 * @param department         所属部门
 * @param securityLevel      安全等级，范围为 1 至 3，默认为 1
 * @param version            业务版本号，最长 64 个字符，默认为 V1.0
 * @param effectiveFrom      生效日期
 * @param effectiveTo        失效日期；长期有效时可为空
 * @param allowedRoles       允许检索文档的角色编码
 * @param authorityLevel     内容权威等级，范围为 1 至 3，默认为 1
 * @param replacesDocumentId 被当前上传版本替代的旧文档 ID
 */
public record DocumentUploadRequest(
        @NotNull(message = "请选择要上传的文件")
        MultipartFile file,

        @NotBlank(message = "文档标题不能为空")
        @Size(max = 256, message = "文档标题不能超过 256 个字符")
        String title,

        @NotNull(message = "请选择所属知识库")
        Long knowledgeBaseId,

        @NotBlank(message = "请选择所属部门")
        String department,

        @Min(value = 1, message = "安全等级必须为 1 到 3")
        @Max(value = 3, message = "安全等级必须为 1 到 3")
        Integer securityLevel,

        @NotBlank(message = "版本号不能为空")
        @Size(max = 64, message = "版本号不能超过 64 个字符")
        String version,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate effectiveFrom,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate effectiveTo,

        List<String> allowedRoles,

        @Min(value = 1, message = "权威等级必须为 1 到 3")
        @Max(value = 3, message = "权威等级必须为 1 到 3")
        Integer authorityLevel,

        Long replacesDocumentId
) {
    public DocumentUploadRequest {
        securityLevel = securityLevel == null ? 1 : securityLevel;
        version = version == null ? "V1.0" : version;
        allowedRoles = allowedRoles == null ? List.of() : List.copyOf(allowedRoles);
        authorityLevel = authorityLevel == null ? 1 : authorityLevel;
    }

    /** 将 Web 请求参数转换为不包含文件内容的业务命令。 */
    public DocumentUploadCommand toCommand() {
        return new DocumentUploadCommand(
                title, knowledgeBaseId, department, securityLevel, version,
                effectiveFrom, effectiveTo, allowedRoles, authorityLevel, replacesDocumentId);
    }
}
