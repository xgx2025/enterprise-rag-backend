package com.hope.enterpriserag.security.service;

import com.hope.enterpriserag.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * 邮件服务，负责发送邮箱验证码并校验。
 * <p>
 * 验证码 5 分钟有效，同一邮箱 60 秒内不可重复发送。
 * 验证码存储在 Redis 中，校验成功后立即删除防止重复使用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final StringRedisTemplate redisTemplate;

    @Value("${spring.mail.username}")
    private String from;

    /**
     * 向指定邮箱发送 6 位数字验证码。
     *
     * @param to 目标邮箱地址
     * @throws BusinessException 如果 60 秒内重复发送
     */
    public void sendVerificationCode(String to) {
        String rateLimitKey = "email_code:ratelimit:" + to;
        Boolean canSend = redisTemplate.opsForValue()
                .setIfAbsent(rateLimitKey, "1", 60, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(canSend)) {
            log.warn("验证码发送频率限制触发: email={}", to);
            throw new BusinessException("验证码已发送，请60秒后再试");
        }

        String code = generateCode();
        String codeKey = "email_code:" + to;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject("Enterprise RAG - 邮箱验证码");
        message.setText("您的验证码是：" + code + "，有效期 5 分钟。");

        try {
            mailSender.send(message);
            // 邮件发送成功后才将验证码写入 Redis
            redisTemplate.opsForValue().set(codeKey, code, 5, TimeUnit.MINUTES);
            log.info("验证码发送成功: email={}", to);
        } catch (RuntimeException e) {
            redisTemplate.delete(rateLimitKey);
            log.error("验证码邮件发送失败: email={}", to, e);
            throw e;
        }
    }

    /**
     * 校验邮箱验证码是否正确。
     *
     * @param email 邮箱地址
     * @param code  用户提交的验证码
     * @return true 验证通过，false 验证码不存在或不匹配
     */
    public boolean verifyCode(String email, String code) {
        String codeKey = "email_code:" + email;
        String storedCode = redisTemplate.opsForValue().get(codeKey);
        if (storedCode == null) {
            return false;
        }
        if (storedCode.equals(code)) {
            redisTemplate.delete(codeKey); // 验证成功即删除，防止重复使用
            return true;
        }
        return false;
    }

    /** 生成 6 位随机数字验证码 */
    private String generateCode() {
        return String.format("%06d", new Random().nextInt(1000000));
    }
}
