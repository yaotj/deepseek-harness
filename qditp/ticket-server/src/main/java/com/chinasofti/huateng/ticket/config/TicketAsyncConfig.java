package com.chinasofti.huateng.ticket.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * ticket-server 异步任务线程池配置。
 */
@Configuration
public class TicketAsyncConfig {

    @Bean("appNotifyExecutor")
    public Executor appNotifyExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(10000);
        executor.setThreadNamePrefix("app-notify-");
        executor.initialize();
        return executor;
    }
}
