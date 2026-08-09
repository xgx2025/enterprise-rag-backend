package com.hope.enterpriserag.knowledge.event;

/**
 * 文档完成解析和父子分块后发布的向量化事件。
 * 事件只携带资源标识，正文由消费者在租户约束下从数据库读取。
 *
 * @param documentId 待向量化文档 ID
 * @param taskId     当前摄取任务 ID
 * @param completionStatus 向量化成功后的文档状态；首次摄取为空时默认进入 READY
 */
public record DocumentVectorizationEvent(Long documentId, Long taskId, String completionStatus) {

    /** 首次摄取或失败重试使用的兼容构造器，完成后进入 READY。 */
    public DocumentVectorizationEvent(Long documentId, Long taskId) {
        this(documentId, taskId, "READY");
    }
}
