package com.hope.enterpriserag.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 租户实体，映射 {@code sys_tenant} 表。
 * 系统默认为每个注册用户分配 DEFAULT 租户。
 */
@Data
@TableName("sys_tenant")
public class SysTenant {
    /** 租户主键（雪花 ID） */
    @TableId(type = IdType.INPUT)
    private Long id;
    /** 租户编码，全局唯一 */
    private String tenantCode;
    /** 租户显示名称 */
    private String tenantName;
    /** 状态：1-启用 0-禁用 */
    private Integer status;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 最后更新时间 */
    private LocalDateTime updatedAt;
}
