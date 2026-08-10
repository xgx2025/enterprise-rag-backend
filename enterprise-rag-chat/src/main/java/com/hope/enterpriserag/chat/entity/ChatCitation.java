package com.hope.enterpriserag.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 助手消息引用快照，保存生成当时实际使用的来源元数据与引文。
 */
@Data
@TableName("chat_citation")
public class ChatCitation {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long tenantId;
    private Long messageId;
    private String sourceId;
    private Long documentId;
    private String title;
    private String version;
    private LocalDate effectiveDate;
    private String sectionPath;
    private Integer pageNumber;
    private String quote;
    private Integer securityLevel;
    private Double score;
    private LocalDateTime createdAt;
}
