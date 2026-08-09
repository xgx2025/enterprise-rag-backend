package com.hope.enterpriserag.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 企业知识库实体，对应 {@code kb_knowledge_base} 表，是文档归属和租户隔离的管理单元。
 */
@Data
@TableName("kb_knowledge_base")
public class KnowledgeBase {
    /** 雪花算法生成的知识库主键。 */
    @TableId(type = IdType.INPUT)
    private Long id;
    /** 所属租户 ID。 */
    private Long tenantId;
    private String name;
    private String description;
    private String department;
    /** 默认安全等级，范围为 1 至 3。 */
    private Integer securityLevel;
    /** 生命周期状态：{@code ACTIVE} 或 {@code DISABLED}。 */
    private String status;
    /** 创建用户 ID。 */
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
