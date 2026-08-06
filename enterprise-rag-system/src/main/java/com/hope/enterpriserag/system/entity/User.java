package com.hope.enterpriserag.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统用户实体，映射 {@code sys_user} 表。
 * 使用 MyBatis-Plus 雪花算法生成主键。
 */
@Data
@TableName("sys_user")
public class User {
    /** 用户主键（雪花 ID） */
    @TableId(type = IdType.INPUT)
    private Long id;
    /** 所属租户 ID */
    private Long tenantId;
    /** 登录用户名，全局唯一 */
    private String username;
    /** BCrypt 加密后的密码 */
    private String password;
    /** 真实姓名 */
    private String realName;
    /** 邮箱，用于验证码和密码重置 */
    private String email;
    /** 手机号 */
    private String phone;
    /** 状态：1-启用 0-禁用 */
    private Integer status;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 最后更新时间 */
    private LocalDateTime updatedAt;
}
