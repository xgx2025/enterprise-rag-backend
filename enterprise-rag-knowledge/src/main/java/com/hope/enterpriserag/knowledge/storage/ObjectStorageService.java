package com.hope.enterpriserag.knowledge.storage;

import java.io.InputStream;
import java.time.Duration;

/**
 * 企业文档原文件的对象存储抽象。
 * 实现需要保证对象默认不可公开读取，并通过短期签名地址提供受控访问。
 */
public interface ObjectStorageService {
    /**
     * 上传对象。
     *
     * @param objectKey    Bucket 内唯一对象键
     * @param inputStream  文件内容流，生命周期由调用方管理
     * @param contentLength 内容长度，单位为字节
     * @param contentType  MIME 类型，可为空
     */
    void upload(String objectKey, InputStream inputStream, long contentLength, String contentType);

    /**
     * 下载对象并返回内容流，调用方负责关闭流。
     *
     * @param objectKey Bucket 内对象键
     * @return 对象内容流
     */
    InputStream download(String objectKey);

    /**
     * 生成只读临时访问地址，禁止在日志中输出返回值。
     *
     * @param objectKey Bucket 内对象键
     * @param expiration 地址有效时长
     * @return 带签名的临时访问地址
     */
    String generateReadUrl(String objectKey, Duration expiration);

    /** @return 系统配置的临时访问地址有效时长 */
    Duration readUrlExpiration();

    /** @param objectKey 需要删除的 Bucket 内对象键 */
    void delete(String objectKey);

    /** @return 当前使用的 Bucket 名称 */
    String bucketName();

    /** @return 当前业务对象键的公共路径前缀 */
    String objectPrefix();
}
