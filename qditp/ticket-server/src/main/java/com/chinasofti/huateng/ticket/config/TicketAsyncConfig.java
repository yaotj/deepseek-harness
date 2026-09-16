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

/**
 * ticket-server 异步任务线程池与出向 HTTP 客户端配置。
 */
@Configuration
public class TicketAsyncConfig {

    private static final Logger log = LoggerFactory.getLogger(TicketAsyncConfig.class);

    @Bean("appNotifyExecutor")
    public Executor appNotifyExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        // 队列原为 10000，调至 2000：推送的是「当前码状态快照」，积压越深、末尾任务发出时快照越可能已过期，
        // 把过期码推给 APP 比不推更有害（快照过期规则仍待裁决，见 docs/domain/outbox.md 第七节第一项）。
        executor.setQueueCapacity(2000);
        executor.setThreadNamePrefix("app-notify-");
        // 保留 CallerRuns 语义（宁可慢、不可丢：本链路没有任何补偿，丢弃即 APP 永久收不到码），
        // 但拒绝时 MUST 先落 ERROR —— 此时任务会退到闸机请求线程上执行，过闸应答被推送耗时拖长，
        // 这是「过闸变慢」的直接原因，NEVER 让它静默发生。
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

    /**
     * 支付宝出行 21 键 {@code industryDetail} 组装用的专属线程池。
     *
     * <p>2026-09-14 随出站扣费编排从 fep-dev-server 迁入（原 {@code FepDevExecutorConfig}）。
     * <b>NEVER 改回 {@code ForkJoinPool.commonPool}</b>（即 {@code supplyAsync} 不传 Executor 的默认行为）：
     * commonPool 的 parallelism 等于「容器可见 CPU 数 - 1」且被整个 JVM 共用，出站高峰期与任何其它
     * 并行流互相挤占，是一条无法定容的隐式耦合。</p>
     *
     * <p><b>拒绝策略刻意用 {@code AbortPolicy}，NEVER 改成 CallerRuns</b>（与
     * {@link #appNotifyExecutor} 的选择相反，两者语义不同）：池满时抛出 →
     * {@code AlipayIndustryDetailAssembler.assemble} 的 catch 接住 → 返 null →
     * 订单照落、扣费收敛成 RETRY 等人工介入。换成 CallerRuns 会把三个 RPC 的耗时
     * 直接加在过闸应答上、引来 AGM 重发，那比「这一笔明细缺失」更坏。</p>
     *
     * <p><b>NEVER 在被本池执行的任务里再向本池提交并 join</b>：有界池上那是必然死锁
     * （外层任务占着线程等内层任务，并发一上来池子被外层占满、内层永远排不到）。
     * {@code assemble} 里三个 RPC 已拍平到同一层，保持那个形状。</p>
     */
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

    /**
     * 出向通知用的 OkHttp 客户端。
     *
     * <p>原先是 {@code AppNotifyServiceImpl} 的字段级 {@code new OkHttpClient.Builder()}，不受容器管理、
     * 超时改一处要动代码。提成 Bean 后超时集中在此，后续加埋点也只改这里。
     * OkHttp 官方要求**全应用共用一个实例**（内部持有连接池与线程池），**NEVER 改成每次调用 new 一个**。
     */
    @Bean("appNotifyHttpClient")
    public OkHttpClient appNotifyHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
    }
}
