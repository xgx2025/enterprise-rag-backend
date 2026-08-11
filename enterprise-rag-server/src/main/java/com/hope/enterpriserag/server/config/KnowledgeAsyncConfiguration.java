package com.hope.enterpriserag.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 知识摄取异步执行器配置，将文件解析和外部向量服务调用与在线 Chat 流量隔离。
 */
@Configuration
public class KnowledgeAsyncConfiguration {
    /** 文件下载、解析和分块执行器。 */
    @Bean(name = "ingestionTaskExecutor")
    public AsyncTaskExecutor ingestionTaskExecutor() {
        return executor("document-ingestion-", 2, 4, 50);
    }

    /** Embedding、Milvus 写入及元数据同步执行器。 */
    @Bean(name = "vectorizationTaskExecutor")
    public AsyncTaskExecutor vectorizationTaskExecutor() {
        return executor("document-vector-", 2, 6, 100);
    }

    private AsyncTaskExecutor executor(String prefix, int core, int maximum, int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(core);
        executor.setMaxPoolSize(maximum);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(prefix);
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
