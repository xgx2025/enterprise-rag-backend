package com.hope.enterpriserag.knowledge.dto;

import java.time.Instant;

/**
 * 私有对象的短期访问凭证。URL 包含签名信息，不应写入日志或持久化。
 *
 * @param url       只读临时访问地址
 * @param expiresAt 地址失效时间
 */
public record ObjectAccessResponse(String url, Instant expiresAt) {
}
