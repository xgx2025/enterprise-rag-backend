package com.hope.enterpriserag.security.service.impl;

import com.hope.enterpriserag.security.dto.LoginRequest;
import com.hope.enterpriserag.security.dto.LoginResponse;
import com.hope.enterpriserag.security.dto.RegisterRequest;
import com.hope.enterpriserag.security.dto.ResetPasswordRequest;
import com.hope.enterpriserag.system.entity.User;
import com.hope.enterpriserag.system.entity.SysTenant;
import com.hope.enterpriserag.common.exception.AuthException;
import com.hope.enterpriserag.common.exception.BusinessException;
import com.hope.enterpriserag.system.mapper.SysTenantMapper;
import com.hope.enterpriserag.security.service.AuthService;
import com.hope.enterpriserag.security.service.EmailService;
import com.hope.enterpriserag.system.service.UserService;
import com.hope.enterpriserag.security.util.JwtUtil;
import io.jsonwebtoken.Claims;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 认证服务实现。
 * <p>
 * 核心设计：
 * <ul>
 *   <li>Access Token 无状态，过期后由前端用 Refresh Token 静默刷新</li>
 *   <li>Refresh Token 采用 JTI 白名单 + 一次性轮转机制，防止重放攻击</li>
 *   <li>检测到 JTI 不在白名单时，清空该用户所有 Refresh Token（疑似泄露）</li>
 *   <li>密码重置、登出均会清空 Redis 中的 Refresh Token 白名单</li>
 * </ul>
 */
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final SysTenantMapper tenantMapper;

    public AuthServiceImpl(UserService userService,
                           JwtUtil jwtUtil,
                           StringRedisTemplate redisTemplate,
                           PasswordEncoder passwordEncoder,
                           EmailService emailService,
                           SysTenantMapper tenantMapper) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.tenantMapper = tenantMapper;
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        User user = userService.getByUsername(request.getUsername());
        if (user == null) {
            log.warn("登录失败-用户不存在: username={}", request.getUsername());
            throw new AuthException("用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            log.warn("登录失败-账号已禁用: userId={}, username={}", user.getId(), request.getUsername());
            throw new AuthException("账号已被禁用");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("登录失败-密码错误: userId={}, username={}", user.getId(), request.getUsername());
            throw new AuthException("用户名或密码错误");
        }

        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getTenantId());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());

        // 将 refresh token 的 jti 存入 Redis 白名单
        Claims refreshClaims = jwtUtil.parseRefreshToken(refreshToken);
        String jti = refreshClaims.getId();
        String redisKey = "refresh_token:" + user.getId();

        redisTemplate.opsForSet().add(redisKey, jti);
        redisTemplate.expire(redisKey, 7, TimeUnit.DAYS);

        log.info("用户登录成功: userId={}, tenantId={}", user.getId(), user.getTenantId());

        LoginResponse response = new LoginResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);
        response.setExpiresIn(1800000);

        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo();
        userInfo.setUserId(user.getId());
        userInfo.setTenantId(user.getTenantId());
        userInfo.setUsername(user.getUsername());
        userInfo.setRealName(user.getRealName());
        response.setUserInfo(userInfo);

        return response;
    }

    @Override
    public LoginResponse refreshToken(String refreshToken) {
        if (!jwtUtil.validateRefreshToken(refreshToken)) {
            log.warn("刷新令牌无效或已过期");
            throw new AuthException("刷新令牌无效或已过期");
        }

        Claims claims = jwtUtil.parseRefreshToken(refreshToken);
        Long userId = jwtUtil.getUserIdFromToken(claims);
        String jti = claims.getId();

        // 验证 jti 是否在白名单中（防重放）
        String redisKey = "refresh_token:" + userId;
        Boolean exists = redisTemplate.opsForSet().isMember(redisKey, jti);
        if (Boolean.FALSE.equals(exists)) {
            // jti 不在白名单中，可能是重放攻击
            log.warn("刷新令牌重放检测: userId={}, jti={}（令牌已被使用或已失效）", userId, jti);
            // 清除该用户所有 refresh token（强制全部重新登录）
            redisTemplate.delete(redisKey);
            throw new AuthException("刷新令牌已被使用或已失效");
        }

        // 删除旧 jti（一次性使用）
        redisTemplate.opsForSet().remove(redisKey, jti);

        // 签发新令牌对（轮转）
        User user = userService.getById(userId);
        if (user == null || (user.getStatus() != null && user.getStatus() == 0)) {
            log.warn("刷新令牌失败-用户不可用: userId={}", userId);
            throw new AuthException("用户不存在或已被禁用");
        }

        String newAccessToken = jwtUtil.generateAccessToken(user.getId(), user.getTenantId());
        String newRefreshToken = jwtUtil.generateRefreshToken(user.getId());

        Claims newRefreshClaims = jwtUtil.parseRefreshToken(newRefreshToken);
        String newJti = newRefreshClaims.getId();
        redisTemplate.opsForSet().add(redisKey, newJti);
        redisTemplate.expire(redisKey, 7, TimeUnit.DAYS);

        log.debug("令牌轮转成功: userId={}", userId);

        LoginResponse response = new LoginResponse();
        response.setAccessToken(newAccessToken);
        response.setRefreshToken(newRefreshToken);
        response.setExpiresIn(1800000);

        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo();
        userInfo.setUserId(user.getId());
        userInfo.setTenantId(user.getTenantId());
        userInfo.setUsername(user.getUsername());
        userInfo.setRealName(user.getRealName());
        response.setUserInfo(userInfo);

        return response;
    }

    @Override
    public void logout(Long userId) {
        String redisKey = "refresh_token:" + userId;
        redisTemplate.delete(redisKey);
        log.info("用户登出成功: userId={}", userId);
    }

    @Override
    public void register(RegisterRequest request) {
        // 检查用户名是否已存在
        if (userService.existsByUsername(request.getUsername())) {
            log.warn("注册失败-用户名已存在: username={}", request.getUsername());
            throw new BusinessException("用户名已被注册");
        }

        SysTenant defaultTenant = tenantMapper.selectOne(
                new LambdaQueryWrapper<SysTenant>().eq(SysTenant::getTenantCode, "DEFAULT")
        );
        if (defaultTenant == null) {
            log.error("注册失败-默认租户未初始化");
            throw new BusinessException("默认租户未初始化，请联系管理员");
        }

        // 先校验验证码再创建用户，避免验证码错误时已插入脏数据
        if (!emailService.verifyCode(request.getEmail(), request.getCode())) {
            log.warn("注册失败-验证码错误: email={}", request.getEmail());
            throw new BusinessException("验证码错误或已过期");
        }

        // 创建用户
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setRealName(request.getUsername());
        user.setTenantId(defaultTenant.getId());
        user.setStatus(1);

        userService.create(user);
        log.info("用户注册成功: userId={}, username={}, tenantId={}", user.getId(), request.getUsername(), defaultTenant.getId());
    }

    @Override
    public void resetPassword(ResetPasswordRequest request) {
        User user = userService.getByEmail(request.getEmail());
        if (user == null) {
            log.warn("密码重置失败-邮箱未注册: email={}", request.getEmail());
            throw new BusinessException("该邮箱未注册");
        }
        if (!emailService.verifyCode(request.getEmail(), request.getCode())) {
            log.warn("密码重置失败-验证码错误: email={}", request.getEmail());
            throw new BusinessException("验证码错误或已过期");
        }
        userService.updatePassword(user, passwordEncoder.encode(request.getNewPassword()));
        // 密码重置后清除所有 refresh token，强制重新登录
        redisTemplate.delete("refresh_token:" + user.getId());
        log.info("密码重置成功: userId={}, email={}", user.getId(), request.getEmail());
    }
}
