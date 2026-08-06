package com.hope.enterpriserag.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档摄取任务实体，对应 {@code kb_ingestion_task} 表，用于跟踪解析、分块和后续向量化进度。
 */
@Data
@TableName("kb_ingestion_task")
public class IngestionTask {
    /** 雪花算法生成的任务主键。 */
    @TableId(type = IdType.INPUT)
    private Long id;
    /** 租户 ID，用于数据隔离。 */
    private Long tenantId;
    /** 待处理文档 ID。 */
    private Long documentId;
    /** 任务类型，例如 {@code PARSE_AND_CHUNK}。 */
    private String taskType;
    /** 任务状态，例如 {@code PENDING}、{@code RUNNING}、{@code FAILED}。 */
    private String status;
    /** 处理进度百分比，范围为 0 至 100。 */
    private Integer progress;
    /** 当前处理阶段，用于前端展示和故障定位。 */
    private String currentStage;
    /** 当前文档累计提交的重试次数。 */
    private Integer retryCount;
    /** 脱敏并截断后的失败原因。 */
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
