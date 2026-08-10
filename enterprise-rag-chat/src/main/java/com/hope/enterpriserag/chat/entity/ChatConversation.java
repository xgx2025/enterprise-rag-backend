package com.hope.enterpriserag.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户问答会话实体，保存租户边界、知识库范围及检索策略快照。
 */
@Data
@TableName("chat_conversation")
public class ChatConversation {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long tenantId;
    private Long userId;
    private String title;
    /** JSON 数组，保存会话当前知识库 ID 范围。 */
    private String knowledgeBaseIds;
    /** JSON 对象，保存 Dense、Sparse、Rerank 等策略。 */
    private String retrievalStrategy;
    /** 生命周期状态：ACTIVE 或 DELETED。 */
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
