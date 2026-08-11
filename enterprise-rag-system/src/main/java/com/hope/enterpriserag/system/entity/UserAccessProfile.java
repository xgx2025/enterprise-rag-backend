package com.hope.enterpriserag.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户知识访问配置，保存服务端授予的角色和最高文档安全等级。
 * 角色使用逗号分隔的规范化编码，禁止从登录请求或 JWT 自声明权限。
 */
@Data
@TableName("sys_user_access")
public class UserAccessProfile {
    /** 用户 ID，同时作为配置主键。 */
    @TableId(type = IdType.INPUT)
    private Long userId;
    /** 用户所属租户，用于防止配置跨租户误绑定。 */
    private Long tenantId;
    /** 逗号分隔的角色编码，例如 {@code ROLE_USER,ROLE_KB_ADMIN}。 */
    private String roles;
    /** 可读取的最高文档安全等级，范围为 1 至 3。 */
    private Integer maximumSecurityLevel;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
