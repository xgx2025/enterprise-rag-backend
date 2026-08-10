package com.hope.enterpriserag.server.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Chat SSE 有界执行器配置，避免慢模型调用占用 Servlet 请求线程。
 */
@Configuration
@ConditionalOnProperty(prefix = "rag.chat", name = "enabled", havingValue = "true")
public class ChatAsyncConfiguration {
    /** 创建支持中断取消的有界流式任务执行器。 */
    @Bean(name = "chatTaskExecutor")
    public AsyncTaskExecutor chatTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("chat-stream-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
