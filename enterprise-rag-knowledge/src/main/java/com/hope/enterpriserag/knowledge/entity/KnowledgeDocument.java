package com.hope.enterpriserag.knowledge.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 企业知识文档实体，对应 {@code kb_document} 表。
 * 保存文档治理元数据、OSS 对象定位信息、访问控制规则和摄取状态，不保存文件正文。
 */
@Data
@TableName("kb_document")
public class KnowledgeDocument {
    /** 雪花算法生成的文档主键。 */
    @TableId(type = IdType.INPUT)
    private Long id;
    /** 所属租户 ID。 */
    private Long tenantId;
    /** 所属知识库 ID。 */
    private Long knowledgeBaseId;
    private String title;
    /** 上传时的原始文件名。 */
    private String fileName;
    /** 大写文件扩展名。 */
    private String fileType;
    /** 文件大小，单位为字节。 */
    private Long fileSize;
    private String contentType;
    /** 对象存储提供方，当前为 {@code ALIYUN_OSS}。 */
    private String storageProvider;
    private String bucketName;
    /** Bucket 内的对象键，不包含访问签名。 */
    private String objectKey;
    /** 文件内容 SHA-256，用于租户内重复上传校验。 */
    private String contentHash;
    private String version;
    /** 生命周期状态，例如 {@code PROCESSING}、{@code READY}、{@code ACTIVE}、{@code EXPIRED}。 */
    private String status;
    private String department;
    /** 安全等级，范围为 1 至 3。 */
    private Integer securityLevel;
    /** 允许检索该文档的角色编码 JSON 数组。 */
    private String allowedRoles;
    /** 内容权威等级，范围为 1 至 3。 */
    private Integer authorityLevel;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    /** 被当前版本替代的旧文档 ID。 */
    private Long replacesDocumentId;
    /** 文本解析状态。 */
    private String parseStatus;
    /** 向量化状态：{@code PENDING}、{@code RUNNING}、{@code COMPLETED} 或 {@code FAILED}。 */
    private String embeddingStatus;
    /** 摄取处理进度百分比，范围为 0 至 100。 */
    private Integer processProgress;
    /** 最近失败阶段；重试开始或处理成功时必须写入 {@code null} 清除旧值。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String failureStage;
    /** 脱敏并截断后的最近失败原因；仅文档处于失败状态时有效。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String failureMessage;
    /** 可用于检索的子块数量。 */
    private Integer chunkCount;
    private Long createdBy;
    /** 逻辑删除标记：0 表示有效，1 表示已归档。 */
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
