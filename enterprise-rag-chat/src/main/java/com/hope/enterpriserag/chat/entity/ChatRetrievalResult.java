package com.hope.enterpriserag.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 助手消息的最终检索结果快照。
 * 仅保存经过权限过滤、重排并实际进入模型上下文的片段，顺序与来源编号保持一致。
 */
@Data
@TableName("chat_retrieval_result")
public class ChatRetrievalResult {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long tenantId;
    private Long messageId;
    /** 最终上下文中的顺序，从 1 开始。 */
    private Integer rankNumber;
    private String sourceId;
    private Long documentId;
    private String title;
    private String version;
    private LocalDate effectiveDate;
    private String sectionPath;
    private Integer pageNumber;
    /** 实际进入模型上下文的片段快照。 */
    private String content;
    private Integer securityLevel;
    /** 最终重排相关性分数。 */
    private Double score;
    private LocalDateTime createdAt;
}
