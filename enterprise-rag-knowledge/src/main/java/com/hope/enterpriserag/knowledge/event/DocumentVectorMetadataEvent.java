package com.hope.enterpriserag.knowledge.event;

/**
 * 文档状态提交后触发的 Milvus 元数据同步事件。
 *
 * @param tenantId   租户 ID
 * @param documentId 文档 ID
 * @param action     同步或删除动作
 */
public record DocumentVectorMetadataEvent(Long tenantId, Long documentId, Action action) {
    /** Milvus 文档向量的生命周期同步动作。 */
    public enum Action {
        SYNC,
        DELETE
    }
}
