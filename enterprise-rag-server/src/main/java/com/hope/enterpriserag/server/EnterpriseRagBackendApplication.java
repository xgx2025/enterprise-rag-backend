package com.hope.enterpriserag.server;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Enterprise RAG 后端启动类。
 * <p>
 * 自动扫描 {@code com.hope.enterpriserag} 包下所有组件，
 * MyBatis-Plus Mapper 扫描 {@code com.hope.enterpriserag.system.mapper}。
 */
@Slf4j
@EnableAsync
@MapperScan({"com.hope.enterpriserag.system.mapper", "com.hope.enterpriserag.knowledge.mapper"})
@SpringBootApplication(scanBasePackages = "com.hope.enterpriserag")
public class EnterpriseRagBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(EnterpriseRagBackendApplication.class, args);
        log.info("Enterprise RAG 后端服务启动完成");
    }
}
