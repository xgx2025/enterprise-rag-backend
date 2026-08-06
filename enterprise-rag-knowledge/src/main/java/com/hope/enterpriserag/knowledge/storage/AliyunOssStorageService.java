package com.hope.enterpriserag.knowledge.storage;

import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSS;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.ObjectMetadata;
import com.hope.enterpriserag.common.exception.BusinessException;
import com.hope.enterpriserag.knowledge.config.OssProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.time.Duration;
import java.util.Date;

/**
 * 基于阿里云 OSS 的企业文档对象存储实现。
 * 负责原文件上传、解析阶段下载、失败补偿删除及短期预览地址生成。
 */
@Slf4j
@Service
public class AliyunOssStorageService implements ObjectStorageService {
    private final OssProperties properties;
    private final ObjectProvider<OSS> ossClientProvider;

    public AliyunOssStorageService(OssProperties properties, ObjectProvider<OSS> ossClientProvider) {
        this.properties = properties;
        this.ossClientProvider = ossClientProvider;
    }

    @Override
    public void upload(String objectKey, InputStream inputStream, long contentLength, String contentType) {
        OSS client = requireClient();
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(contentLength);
        if (StringUtils.hasText(contentType)) {
            metadata.setContentType(contentType);
        }
        try {
            client.putObject(properties.getBucket(), objectKey, inputStream, metadata);
            log.debug("阿里云OSS对象上传成功: bucket={}, objectKey={}, contentLength={}",
                    properties.getBucket(), objectKey, contentLength);
        } catch (Exception e) {
            log.error("上传文件到阿里云OSS失败: bucket={}, objectKey={}",
                    properties.getBucket(), objectKey, e);
            throw new BusinessException("文件上传至阿里云 OSS 失败，请检查 OSS 配置和网络");
        }
    }

    @Override
    public InputStream download(String objectKey) {
        try {
            InputStream content = requireClient().getObject(properties.getBucket(), objectKey).getObjectContent();
            log.debug("阿里云OSS对象读取成功: bucket={}, objectKey={}", properties.getBucket(), objectKey);
            return content;
        } catch (Exception e) {
            log.error("从阿里云OSS读取文件失败: bucket={}, objectKey={}",
                    properties.getBucket(), objectKey, e);
            throw new BusinessException("读取文档原文件失败");
        }
    }

    @Override
    public String generateReadUrl(String objectKey, Duration expiration) {
        try {
            GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(
                    properties.getBucket(), objectKey, HttpMethod.GET);
            request.setExpiration(new Date(System.currentTimeMillis() + expiration.toMillis()));
            String signedUrl = requireClient().generatePresignedUrl(request).toString();
            log.debug("阿里云OSS签名URL生成成功: bucket={}, objectKey={}, expirationMinutes={}",
                    properties.getBucket(), objectKey, expiration.toMinutes());
            return signedUrl;
        } catch (Exception e) {
            log.error("生成阿里云OSS签名URL失败: bucket={}, objectKey={}",
                    properties.getBucket(), objectKey, e);
            throw new BusinessException("生成文档预览地址失败");
        }
    }

    @Override
    public Duration readUrlExpiration() {
        return Duration.ofMinutes(Math.max(1, Math.min(60, properties.getSignedUrlExpirationMinutes())));
    }

    @Override
    public void delete(String objectKey) {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            requireClient().deleteObject(properties.getBucket(), objectKey);
            log.debug("阿里云OSS对象删除成功: bucket={}, objectKey={}", properties.getBucket(), objectKey);
        } catch (Exception e) {
            log.warn("清理阿里云OSS对象失败: bucket={}, objectKey={}",
                    properties.getBucket(), objectKey, e);
        }
    }

    @Override
    public String bucketName() {
        ensureConfigured();
        return properties.getBucket();
    }

    @Override
    public String objectPrefix() {
        String prefix = properties.getObjectPrefix();
        return prefix == null || prefix.isBlank() ? "enterprise-rag" : prefix.replaceAll("^/+|/+$", "");
    }

    private OSS requireClient() {
        ensureConfigured();
        OSS client = ossClientProvider.getIfAvailable();
        if (client == null) {
            throw new BusinessException("阿里云 OSS 客户端未初始化，请检查访问凭证");
        }
        return client;
    }

    private void ensureConfigured() {
        if (!properties.isEnabled()) {
            throw new BusinessException("阿里云 OSS 未启用，请设置 OSS_ENABLED=true");
        }
        if (!StringUtils.hasText(properties.getEndpoint())
                || !StringUtils.hasText(properties.getRegion())
                || !StringUtils.hasText(properties.getBucket())) {
            throw new BusinessException("阿里云 OSS 配置不完整，请检查 Endpoint、Region 和 Bucket");
        }
    }
}
