package com.hope.enterpriserag.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Chat 请求幂等占位，同一租户用户的 requestId 只能成功领取一次。
 */
@Data
@TableName("chat_request_claim")
public class ChatRequestClaim {
    @TableId(type = IdType.INPUT)
    private Long id;
    private Long tenantId;
    private Long userId;
    private String requestId;
    private LocalDateTime createdAt;
}
