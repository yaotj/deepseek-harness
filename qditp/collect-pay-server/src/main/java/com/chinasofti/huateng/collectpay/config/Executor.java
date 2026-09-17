package com.chinasofti.huateng.collectpay.config;

import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadPoolExecutor;

/** Created by tian on 2021/12/17. */
@EnableAsync
@Component
public class Executor {

    //线程池维护线程的最少数量
    private static final int CORE_POOL_SIZE = 10;
    //线程池维护线程的最大数量
    private static final int MAX_POOL_SIZE = 50;
    //缓存队列
    private static final int QUEUE_CAPACITY = 10;
    //允许的空闲时间
    private static final int KEEP_ALIVE = 60;

    @Bean("tvmexecutor")
    public ThreadPoolTaskExecutor executor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(MAX_POOL_SIZE);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setThreadNamePrefix("executor-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setKeepAliveSeconds(KEEP_ALIVE);
        executor.initialize();
        return executor;
    }

}