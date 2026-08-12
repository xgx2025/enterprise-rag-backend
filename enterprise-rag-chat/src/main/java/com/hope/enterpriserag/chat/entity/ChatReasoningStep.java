package com.hope.enterpriserag.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 助手消息的用户可见推理摘要步骤，只保存阶段描述和安全统计，不保存模型原始思维链。
 */
@Data
@TableName("chat_reasoning_step")
public class ChatReasoningStep {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long tenantId;
    private Long messageId;
    /** 同一消息内的展示顺序，从 0 开始。 */
    private Integer sequenceNumber;
    /** 稳定阶段标识，例如 retrieval。 */
    private String stepKey;
    private String title;
    /** 面向用户的安全摘要，最大 500 字符。 */
    private String detail;
    /** 步骤状态：RUNNING、COMPLETED 或 FAILED。 */
    private String status;
    private LocalDateTime createdAt;
}
