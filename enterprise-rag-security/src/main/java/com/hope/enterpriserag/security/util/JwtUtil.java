package com.hope.enterpriserag.security.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 令牌工具类，负责 Access Token 和 Refresh Token 的生成、解析和校验。
 * <p>
 * Access Token 和 Refresh Token 使用独立的密钥签名，互不通用。
 * Refresh Token 通过 JTI（JWT ID）实现一次性轮转机制。
 */
@Slf4j
@Component
public class JwtUtil {

    /** Access Token 签名密钥 */
    private final SecretKey accessSecret;
    /** Refresh Token 签名密钥 */
    private final SecretKey refreshSecret;
    /** Access Token 有效期（毫秒） */
    private final long accessExpiration;
    /** Refresh Token 有效期（毫秒） */
    private final long refreshExpiration;

    public JwtUtil(
            @Value("${jwt.access-token.secret}") String accessSecretStr,
            @Value("${jwt.refresh-token.secret}") String refreshSecretStr,
            @Value("${jwt.access-token.expiration}") long accessExpiration,
            @Value("${jwt.refresh-token.expiration}") long refreshExpiration) {
        this.accessSecret = Keys.hmacShaKeyFor(accessSecretStr.getBytes());
        this.refreshSecret = Keys.hmacShaKeyFor(refreshSecretStr.getBytes());
        this.accessExpiration = accessExpiration;
        this.refreshExpiration = refreshExpiration;
    }

    /** 生成 Access Token，包含 userId、tenantId */
    public String generateAccessToken(Long userId, Long tenantId) {
        Date now = new Date();
        return Jwts.builder()
                .issuer("enterprise-rag")
                .subject(String.valueOf(userId))
                .claim("type", "access")
                .claim("tenantId", tenantId)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessExpiration))
                .signWith(accessSecret)
                .compact();
    }

    /** 生成 Refresh Token，包含随机 JTI 用于防重放 */
    public String generateRefreshToken(Long userId) {
        Date now = new Date();
        return Jwts.builder()
                .issuer("enterprise-rag")
                .subject(String.valueOf(userId))
                .id(UUID.randomUUID().toString())
                .claim("type", "refresh")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + refreshExpiration))
                .signWith(refreshSecret)
                .compact();
    }

    /** 解析 Access Token 的 Claims */
    public Claims parseAccessToken(String token) {
        return Jwts.parser()
                .verifyWith(accessSecret)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** 解析 Refresh Token 的 Claims */
    public Claims parseRefreshToken(String token) {
        return Jwts.parser()
                .verifyWith(refreshSecret)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** 校验 Access Token 签名和类型是否有效 */
    public boolean validateAccessToken(String token) {
        try {
            Claims claims = parseAccessToken(token);
            return "access".equals(claims.get("type"));
        } catch (Exception e) {
            log.debug("Access Token 校验失败: {}", e.getMessage());
            return false;
        }
    }

    /** 校验 Refresh Token 签名和类型是否有效 */
    public boolean validateRefreshToken(String token) {
        try {
            Claims claims = parseRefreshToken(token);
            return "refresh".equals(claims.get("type"));
        } catch (Exception e) {
            log.debug("Refresh Token 校验失败: {}", e.getMessage());
            return false;
        }
    }

    /** 从 Claims 中提取 userId */
    public Long getUserIdFromToken(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }
}
