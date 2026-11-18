package com.chinasofti.huateng.acc.security.server.reconnect.handle;

import com.chinasofti.huateng.acc.security.server.reconnect.thread.ReConnectJob;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.netty.channel.ChannelHandlerContext;
import org.springframework.stereotype.Component;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * 重连管理。
 *
 * <p>修复原实现的任务泄漏：同一时刻全局最多一个周期性重连任务，
 * 重复触发幂等；池补满后取消任务但保留执行器复用，
 * 避免高频断连下任务叠加导致的并发超建连接（过冲）。</p>
 */
@Component
public final class ReConnectManager {

    private ScheduledExecutorService scheduledExecutorService;
    private ScheduledFuture<?> reconnectFuture;

    /**
     * 触发重连（幂等：已有重连任务在跑时不重复挂任务）。
     */
    public synchronized void reConnect(ChannelHandlerContext ctx) {
        buildExecutor();
        if (reconnectFuture == null || reconnectFuture.isDone() || reconnectFuture.isCancelled()) {
            reconnectFuture = scheduledExecutorService.scheduleWithFixedDelay(
                    new ReConnectJob(ctx), 2, 10, TimeUnit.SECONDS);
        }
    }

    /**
     * 池已补满时停止重连任务（保留执行器复用）。
     */
    public synchronized void reConnectSuccess() {
        if (reconnectFuture != null) {
            reconnectFuture.cancel(false);
            reconnectFuture = null;
        }
    }

    private void buildExecutor() {
        if (scheduledExecutorService == null || scheduledExecutorService.isShutdown()) {
            ThreadFactory sche = new ThreadFactoryBuilder()
                    .setNameFormat("reConnect-job-%d")
                    .setDaemon(true)
                    .build();
            scheduledExecutorService = new ScheduledThreadPoolExecutor(1, sche);
        }
    }
}
