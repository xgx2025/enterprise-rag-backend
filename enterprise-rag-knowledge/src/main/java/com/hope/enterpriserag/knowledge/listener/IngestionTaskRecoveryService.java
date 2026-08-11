package com.hope.enterpriserag.knowledge.listener;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.hope.enterpriserag.knowledge.config.IngestionRecoveryProperties;
import com.hope.enterpriserag.knowledge.entity.IngestionTask;
import com.hope.enterpriserag.knowledge.event.DocumentUploadedEvent;
import com.hope.enterpriserag.knowledge.event.DocumentVectorizationEvent;
import com.hope.enterpriserag.knowledge.mapper.IngestionTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 基于数据库任务表的摄取恢复器，弥补进程内事件在提交后宕机时可能丢失的问题。
 * 消费者仍通过条件更新原子领取任务，因此重复投递不会重复执行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IngestionTaskRecoveryService {
    private final IngestionTaskMapper taskMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final IngestionRecoveryProperties properties;

    @Value("${rag.vectorization.enabled:false}")
    private boolean vectorizationEnabled;

    /** 周期性恢复等待任务以及超过失联阈值的运行中任务。 */
    @Scheduled(fixedDelayString = "${rag.ingestion.recovery.scan-interval-millis:60000}",
            initialDelayString = "${rag.ingestion.recovery.scan-interval-millis:60000}")
    public void recover() {
        if (!properties.isEnabled()) {
            return;
        }
        int limit = Math.max(1, Math.min(200, properties.getBatchSize()));
        LocalDateTime staleBefore = LocalDateTime.now()
                .minusMinutes(Math.max(5, properties.getStaleMinutes()));
        recoverStaleRunning(staleBefore, limit);
        List<IngestionTask> waiting = taskMapper.selectList(new LambdaQueryWrapper<IngestionTask>()
                .in(IngestionTask::getStatus, "PENDING", "WAITING_VECTOR")
                .orderByAsc(IngestionTask::getUpdatedAt)
                .last("LIMIT " + limit));
        for (IngestionTask task : waiting) {
            publish(task);
        }
    }

    private void recoverStaleRunning(LocalDateTime staleBefore, int limit) {
        List<IngestionTask> stale = taskMapper.selectList(new LambdaQueryWrapper<IngestionTask>()
                .eq(IngestionTask::getStatus, "RUNNING")
                .le(IngestionTask::getUpdatedAt, staleBefore)
                .orderByAsc(IngestionTask::getUpdatedAt)
                .last("LIMIT " + limit));
        for (IngestionTask task : stale) {
            String waitingStatus = isVectorTask(task) ? "WAITING_VECTOR" : "PENDING";
            int updated = taskMapper.update(null, new UpdateWrapper<IngestionTask>()
                    .eq("id", task.getId())
                    .eq("status", "RUNNING")
                    .le("updated_at", staleBefore)
                    .set("status", waitingStatus)
                    .set("current_stage", "RECOVERED")
                    .set("updated_at", LocalDateTime.now()));
            if (updated == 1) {
                task.setStatus(waitingStatus);
                log.warn("恢复失联的文档摄取任务: tenantId={}, documentId={}, taskId={}, targetStatus={}",
                        task.getTenantId(), task.getDocumentId(), task.getId(), waitingStatus);
                publish(task);
            }
        }
    }

    private void publish(IngestionTask task) {
        if ("PENDING".equals(task.getStatus())) {
            eventPublisher.publishEvent(new DocumentUploadedEvent(task.getDocumentId(), task.getId()));
        } else if ("WAITING_VECTOR".equals(task.getStatus()) && vectorizationEnabled) {
            eventPublisher.publishEvent(new DocumentVectorizationEvent(task.getDocumentId(), task.getId(),
                    completionStatus(task)));
        }
    }

    private boolean isVectorTask(IngestionTask task) {
        return "VECTORIZE".equals(task.getTaskType())
                || (task.getTaskType() != null && task.getTaskType().startsWith("REINDEX"))
                || "EMBEDDING".equals(task.getCurrentStage())
                || "MILVUS_UPSERT".equals(task.getCurrentStage());
    }

    private String completionStatus(IngestionTask task) {
        String type = task.getTaskType();
        if (type != null && type.startsWith("REINDEX_") && type.length() > "REINDEX_".length()) {
            return type.substring("REINDEX_".length());
        }
        return "READY";
    }
}
