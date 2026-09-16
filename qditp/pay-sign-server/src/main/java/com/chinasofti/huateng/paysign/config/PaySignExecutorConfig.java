package com.chinasofti.huateng.paysign.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
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
        executor.setTaskDecorator(mdcTaskDecorator());
        executor.initialize();
        return executor;
    }

    /**
     * 把提交线程的 MDC（含 traceId / spanId）透传到异步线程。
     * 线程池会复用线程，靠 InheritableThreadLocal 只在建线程时继承一次，必须在每个任务前后显式设置与清理。
     */
    private TaskDecorator mdcTaskDecorator() {
        return runnable -> {
            Map<String, String> parentContext = MDC.getCopyOfContextMap();
            return () -> {
                Map<String, String> previous = MDC.getCopyOfContextMap();
                if (parentContext == null) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(parentContext);
                }
                try {
                    runnable.run();
                } finally {
                    if (previous == null) {
                        MDC.clear();
                    } else {
                        MDC.setContextMap(previous);
                    }
                }
            };
        };
    }
}
