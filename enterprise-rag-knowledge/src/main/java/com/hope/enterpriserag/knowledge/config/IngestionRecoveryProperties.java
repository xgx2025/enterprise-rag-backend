package com.hope.enterpriserag.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文档摄取任务恢复配置，用于重新投递提交后丢失的事件和回收进程崩溃遗留的任务。
 */
@Data
@Component
@ConfigurationProperties(prefix = "rag.ingestion.recovery")
public class IngestionRecoveryProperties {
    /** 是否启用数据库任务恢复扫描。 */
    private boolean enabled = true;
    /** RUNNING 多久没有更新时间后视为失联，单位分钟。 */
    private int staleMinutes = 30;
    /** 单次扫描最多恢复的任务数。 */
    private int batchSize = 50;
}
