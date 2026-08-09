package com.hope.enterpriserag.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 阿里云 OSS 存储配置，对应 {@code aliyun.oss} 配置前缀。
 * 访问凭证不保存在本对象中，由阿里云 SDK 从环境变量凭证提供器读取。
 */
@Data
@ConfigurationProperties(prefix = "aliyun.oss")
public class OssProperties {
    /** 是否启用 OSS 文件存储。 */
    private boolean enabled;
    /** OSS 服务 Endpoint，例如 {@code oss-cn-hangzhou.aliyuncs.com}。 */
    private String endpoint;
    /** Bucket 所在地域，用于 V4 签名。 */
    private String region;
    /** 保存企业原始文档的私有 Bucket 名称。 */
    private String bucket;
    /** 文档对象在 Bucket 中使用的公共路径前缀。 */
    private String objectPrefix = "enterprise-rag";
    /** 原文预览签名地址的有效时间，单位为分钟。 */
    private long signedUrlExpirationMinutes = 10;
}
