package com.hope.enterpriserag.knowledge.command;

import java.io.IOException;
import java.io.InputStream;

/**
 * 文档上传文件契约，隔离知识应用服务与 Spring MVC multipart 类型。
 * 实现必须允许在一次上传流程中多次打开内容流，以支持摘要计算和对象存储上传。
 */
public interface DocumentUploadFile {

    /** 返回客户端提交的原始文件名。 */
    String originalFilename();

    /** 返回文件 MIME 类型；客户端未提供时可以为空。 */
    String contentType();

    /** 返回文件大小，单位为字节。 */
    long size();

    /** 判断文件是否未携带有效内容。 */
    boolean empty();

    /**
     * 打开新的文件内容流。
     *
     * @return 可独立关闭的文件内容流
     * @throws IOException 无法读取上传内容时抛出
     */
    InputStream openStream() throws IOException;
}
