package com.hope.enterpriserag.knowledge.event;

/**
 * 文档原文件和元数据完成持久化后发布的领域事件，用于异步触发解析与分块任务。
 *
 * @param documentId 待处理文档 ID
 * @param taskId     对应的摄取任务 ID
 */
public record DocumentUploadedEvent(Long documentId, Long taskId) {
}
