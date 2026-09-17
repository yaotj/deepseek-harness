package com.chinasofti.huateng.ticket.config;

import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** ticket-server 异步任务线程池与出向 HTTP 客户端配置。 */
@Configuration
public class TicketAsyncConfig {

    private static final Logger log = LoggerFactory.getLogger(TicketAsyncConfig.class);

    @Bean("appNotifyExecutor")
    public Executor appNotifyExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(2000);
        executor.setThreadNamePrefix("app-notify-");
        executor.setRejectedExecutionHandler((runnable, threadPoolExecutor) -> {
            log.error("APP 通知线程池已满，任务退回调用方线程执行，过闸应答将被推送耗时拖长, "
                            + "activeCount={}, queueSize={}, completedTaskCount={}",
                    threadPoolExecutor.getActiveCount(),
                    threadPoolExecutor.getQueue().size(),
                    threadPoolExecutor.getCompletedTaskCount());
            if (!threadPoolExecutor.isShutdown()) {
                runnable.run();
            }
        });
        executor.initialize();
        return executor;
    }

    /** 支付宝出行 21 键 {@code industryDetail} 组装用的专属线程池。 */
    @Bean("industryDetailExecutor")
    public Executor industryDetailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("industry-detail-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /** 出向通知用的 OkHttp 客户端。 */
    @Bean("appNotifyHttpClient")
    public OkHttpClient appNotifyHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
    }
}
