package com.hope.enterpriserag.system.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hope.enterpriserag.system.entity.User;
import com.hope.enterpriserag.system.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 用户领域服务，封装用户相关的数据库操作。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserMapper userMapper;

    /** 根据用户名精确查询用户 */
    public User getByUsername(String username) {
        return userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username)
        );
    }

    /** 根据邮箱精确查询用户 */
    public User getByEmail(String email) {
        return userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getEmail, email)
        );
    }

    /** 根据主键查询用户 */
    public User getById(Long userId) {
        return userMapper.selectById(userId);
    }

    /** 判断用户名是否已存在 */
    public boolean existsByUsername(String username) {
        return userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username)
        ) > 0;
    }

    /** 创建新用户，自动填充雪花 ID 和时间字段 */
    public void create(User user) {
        if (user.getId() == null) {
            user.setId(IdUtil.getSnowflakeNextId());
        }
        user.setCreatedAt(java.time.LocalDateTime.now());
        user.setUpdatedAt(java.time.LocalDateTime.now());
        userMapper.insert(user);
        log.debug("用户创建成功: userId={}, username={}", user.getId(), user.getUsername());
    }

    /** 更新用户密码并刷新更新时间 */
    public void updatePassword(User user, String encodedPassword) {
        user.setPassword(encodedPassword);
        user.setUpdatedAt(java.time.LocalDateTime.now());
        userMapper.updateById(user);
    }
}
