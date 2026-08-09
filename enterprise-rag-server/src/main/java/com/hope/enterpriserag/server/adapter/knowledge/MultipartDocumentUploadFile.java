package com.hope.enterpriserag.server.adapter.knowledge;

import com.hope.enterpriserag.knowledge.command.DocumentUploadFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * Spring MVC {@link MultipartFile} 到知识模块上传文件契约的适配器。
 */
public record MultipartDocumentUploadFile(MultipartFile delegate) implements DocumentUploadFile {

    @Override
    public String originalFilename() {
        return delegate.getOriginalFilename();
    }

    @Override
    public String contentType() {
        return delegate.getContentType();
    }

    @Override
    public long size() {
        return delegate.getSize();
    }

    @Override
    public boolean empty() {
        return delegate.isEmpty();
    }

    @Override
    public InputStream openStream() throws IOException {
        return delegate.getInputStream();
    }
}
