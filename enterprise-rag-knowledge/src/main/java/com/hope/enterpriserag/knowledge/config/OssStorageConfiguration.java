package com.hope.enterpriserag.knowledge.config;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.auth.CredentialsProviderFactory;
import com.aliyun.oss.common.comm.SignVersion;
import com.aliyuncs.exceptions.ClientException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 阿里云 OSS 客户端配置，仅在显式启用 OSS 时创建客户端并使用 V4 签名。
 */
@Configuration
@EnableConfigurationProperties(OssProperties.class)
public class OssStorageConfiguration {

    /**
     * 创建由 Spring 托管的 OSS 客户端，容器关闭时自动释放连接资源。
     *
     * @param properties OSS 连接配置
     * @return 已配置的 OSS 客户端
     * @throws ClientException 无法从环境变量加载访问凭证时抛出
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "aliyun.oss", name = "enabled", havingValue = "true")
    public OSS ossClient(OssProperties properties) throws ClientException {
        ClientBuilderConfiguration configuration = new ClientBuilderConfiguration();
        configuration.setSignatureVersion(SignVersion.V4);
        return OSSClientBuilder.create()
                .endpoint(properties.getEndpoint())
                .credentialsProvider(CredentialsProviderFactory.newEnvironmentVariableCredentialsProvider())
                .clientConfiguration(configuration)
                .region(properties.getRegion())
                .build();
    }
}
