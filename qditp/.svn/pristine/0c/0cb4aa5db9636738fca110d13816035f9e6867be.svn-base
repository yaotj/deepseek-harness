package com.chinasofti.huateng.paysign.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * pay-sign 异步任务线程池配置。
 */
@Configuration
@EnableAsync
public class PaySignExecutorConfig {

    private static final Logger log = LoggerFactory.getLogger(PaySignExecutorConfig.class);

    @Value("${app.notify.executor.core-pool-size:4}")
    private int corePoolSize;

    @Value("${app.notify.executor.max-pool-size:16}")
    private int maxPoolSize;

    @Value("${app.notify.executor.queue-capacity:1000}")
    private int queueCapacity;

    @Value("${app.notify.executor.thread-name-prefix:app-notify-}")
    private String threadNamePrefix;

    @Bean("notifyExecutor")
    public Executor notifyExecutor() {
        log.info("start notifyExecutor");

        ThreadPoolTaskExecutor executor = new VisibleThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
