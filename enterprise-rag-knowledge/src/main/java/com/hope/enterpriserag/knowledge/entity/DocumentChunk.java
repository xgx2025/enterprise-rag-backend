package com.hope.enterpriserag.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档分块持久化实体，对应 {@code kb_chunk} 表。
 * 父块用于保留上下文，子块用于后续向量化和检索。
 */
@Data
@TableName("kb_chunk")
public class DocumentChunk {
    /** 雪花算法生成的分块主键。 */
    @TableId(type = IdType.INPUT)
    private Long id;
    /** 租户 ID，用于数据隔离。 */
    private Long tenantId;
    /** 所属文档 ID。 */
    private Long documentId;
    /** 父块 ID；父块记录本身为 {@code null}。 */
    private Long parentChunkId;
    /** 分块在文档内的顺序，从 0 开始。 */
    private Integer chunkIndex;
    /** 提取并规范化后的分块正文。 */
    private String content;
    /** 章节层级路径。 */
    private String sectionPath;
    /** 原文件页码；解析器无法识别时为空。 */
    private Integer pageNumber;
    /** 用于评估模型上下文占用的 Token 估算值。 */
    private Integer tokenCount;
    /** 向量化状态：{@code PENDING}、{@code RUNNING}、{@code COMPLETED} 或 {@code FAILED}。 */
    private String embeddingStatus;
    /** 分块类型等扩展元数据 JSON。 */
    private String metadataJson;
    private LocalDateTime createdAt;
}
