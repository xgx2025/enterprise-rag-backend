package com.hope.enterpriserag.system.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hope.enterpriserag.system.entity.User;
import com.hope.enterpriserag.system.entity.UserAccessProfile;
import com.hope.enterpriserag.system.mapper.UserAccessProfileMapper;
import com.hope.enterpriserag.system.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 用户领域服务，封装用户相关的数据库操作。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserMapper userMapper;
    private final UserAccessProfileMapper accessProfileMapper;

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
    @Transactional
    public void create(User user) {
        if (user.getId() == null) {
            user.setId(IdUtil.getSnowflakeNextId());
        }
        user.setCreatedAt(java.time.LocalDateTime.now());
        user.setUpdatedAt(java.time.LocalDateTime.now());
        userMapper.insert(user);
        UserAccessProfile accessProfile = new UserAccessProfile();
        accessProfile.setUserId(user.getId());
        accessProfile.setTenantId(user.getTenantId());
        accessProfile.setRoles("ROLE_USER");
        accessProfile.setMaximumSecurityLevel(1);
        accessProfile.setCreatedAt(user.getCreatedAt());
        accessProfile.setUpdatedAt(user.getUpdatedAt());
        accessProfileMapper.insert(accessProfile);
        log.debug("用户创建成功: userId={}, username={}", user.getId(), user.getUsername());
    }

    /**
     * 从服务端访问配置填充用户角色与安全等级；缺少配置时采用最小权限。
     */
    public void loadAccessProfile(User user) {
        if (user == null || user.getId() == null || user.getTenantId() == null) {
            return;
        }
        UserAccessProfile profile = accessProfileMapper.selectOne(
                new LambdaQueryWrapper<UserAccessProfile>()
                        .eq(UserAccessProfile::getUserId, user.getId())
                        .eq(UserAccessProfile::getTenantId, user.getTenantId()));
        if (profile == null) {
            user.setRoles(Set.of("ROLE_USER"));
            user.setMaximumSecurityLevel(1);
            log.warn("用户缺少访问配置，按最小权限认证: tenantId={}, userId={}",
                    user.getTenantId(), user.getId());
            return;
        }
        user.setRoles(parseRoles(profile.getRoles()));
        int level = profile.getMaximumSecurityLevel() == null ? 1 : profile.getMaximumSecurityLevel();
        user.setMaximumSecurityLevel(Math.max(1, Math.min(3, level)));
    }

    private Set<String> parseRoles(String roles) {
        if (roles == null || roles.isBlank()) {
            return Set.of("ROLE_USER");
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        Arrays.stream(roles.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(value -> value.toUpperCase(Locale.ROOT))
                .filter(value -> value.matches("ROLE_[A-Z0-9_]{1,59}"))
                .forEach(normalized::add);
        normalized.add("ROLE_USER");
        return Set.copyOf(normalized);
    }

    /** 更新用户密码并刷新更新时间 */
    public void updatePassword(User user, String encodedPassword) {
        user.setPassword(encodedPassword);
        user.setUpdatedAt(java.time.LocalDateTime.now());
        userMapper.updateById(user);
    }
}
