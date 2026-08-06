package com.hope.enterpriserag.knowledge.event;

/**
 * 文档完成解析和父子分块后发布的向量化事件。
 * 事件只携带资源标识，正文由消费者在租户约束下从数据库读取。
 *
 * @param documentId 待向量化文档 ID
 * @param taskId     当前摄取任务 ID
 */
public record DocumentVectorizationEvent(Long documentId, Long taskId) {
}
