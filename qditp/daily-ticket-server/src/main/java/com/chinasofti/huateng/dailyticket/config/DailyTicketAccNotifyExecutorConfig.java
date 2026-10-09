package com.chinasofti.huateng.dailyticket.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/** 日票 ACC 发售通知异步线程池。 */
@Configuration
public class DailyTicketAccNotifyExecutorConfig {

    @Bean("dailyTicketAccNotifyExecutor")
    public Executor dailyTicketAccNotifyExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("daily-ticket-acc-notify-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
