package com.chinasofti.huateng.fep.dev.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/** 异步任务线程池配置。 */
@Configuration
public class AsyncConfig {

    /**
     * AGM 心跳异步落库专用线程池。
     *
     * <p>使用有界队列 + 丢弃最老任务策略，避免心跳高峰拖垮内存；
     * 心跳本身只保留最新状态，丢失旧任务对业务无影响。</p>
     */
    @Bean(name = "agmHeartbeatExecutor")
    public Executor agmHeartbeatExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(256);
        executor.setThreadNamePrefix("agm-hb-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardOldestPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
