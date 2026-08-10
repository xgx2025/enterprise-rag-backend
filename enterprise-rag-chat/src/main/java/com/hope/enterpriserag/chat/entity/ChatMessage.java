package com.hope.enterpriserag.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话消息实体；助手消息通过 parentMessageId 关联触发它的用户问题。
 */
@Data
@TableName("chat_message")
public class ChatMessage {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long tenantId;
    private Long conversationId;
    /** 助手消息对应的用户消息 ID；用户消息为空。 */
    private Long parentMessageId;
    /** 重新生成时指向被替代的助手消息。 */
    private Long regeneratedFromId;
    /** 消息角色：USER 或 ASSISTANT。 */
    private String role;
    private String content;
    /** 处理状态：RUNNING、COMPLETED、FAILED 或 CANCELLED。 */
    private String status;
    /** 回答可信状态：SUPPORTED、PARTIAL 或 INSUFFICIENT。 */
    private String answerStatus;
    private String traceId;
    private String errorCode;
    private String errorMessage;
    /** 检索统计 JSON，不包含正文。 */
    private String retrievalStats;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
