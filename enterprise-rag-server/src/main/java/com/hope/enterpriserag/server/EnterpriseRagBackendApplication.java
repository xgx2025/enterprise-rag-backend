package com.hope.enterpriserag.server;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enterprise RAG 后端启动类。
 * <p>
 * 自动扫描 {@code com.hope.enterpriserag} 包下所有组件，
 * MyBatis-Plus Mapper 同时扫描系统主数据、知识业务和可信问答的持久化包。
 */
@Slf4j
@EnableAsync
@EnableScheduling
@MapperScan({
        "com.hope.enterpriserag.system.mapper",
        "com.hope.enterpriserag.knowledge.mapper",
        "com.hope.enterpriserag.chat.mapper"
})
@SpringBootApplication(scanBasePackages = "com.hope.enterpriserag")
public class EnterpriseRagBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(EnterpriseRagBackendApplication.class, args);
        log.info("Enterprise RAG 后端服务启动完成");
    }
}
