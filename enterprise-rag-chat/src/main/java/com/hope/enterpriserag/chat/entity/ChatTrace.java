package com.hope.enterpriserag.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 单次回答 Trace 审计实体，保存检索范围、策略、耗时、模型及 Token 用量，不保存 Prompt。
 */
@Data
@TableName("chat_trace")
public class ChatTrace {
    @TableId(type = IdType.INPUT)
    private Long id;
    private String traceId;
    private Long tenantId;
    private Long userId;
    private Long conversationId;
    private Long userMessageId;
    private Long assistantMessageId;
    private String knowledgeBaseIds;
    private String retrievalStrategy;
    private String retrievalStats;
    private String retrievalTiming;
    private String model;
    private String answerStatus;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private LocalDateTime createdAt;
}
