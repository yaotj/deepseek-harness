package com.chinasofti.huateng.acc.security.server.socket;

import com.chinasofti.huateng.acc.security.server.config.ChannelCache;
import com.chinasofti.huateng.acc.security.server.config.SecurityConfig;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 加密机连接客户端。
 *
 * <p>改为全事件驱动：共享一个全局 {@link NioEventLoopGroup}（daemon 线程），
 * connect 非阻塞 + 监听器回调，不再为每条连接独占一个线程/一个事件循环组。</p>
 *
 * <p>连接建立失败不占池位：由 {@code ClientHeartBeatHandlerImpl} 周期性按
 * 「目标池大小 - 存活 - 在途」缺口补齐，失败重试天然包含在该循环里。</p>
 */
@Component
public class SocketClient {
    private static final Logger logger = LoggerFactory.getLogger(SocketClient.class);

    private static final EventLoopGroup SHARED_GROUP = new NioEventLoopGroup(0,
            new ThreadFactoryBuilder().setNameFormat("hsm-io-%d").setDaemon(true).build());

    /** 已发起但尚未回调的 connect 数，用于补齐缺口时防过冲。 */
    private static final AtomicInteger PENDING_CONNECTS = new AtomicInteger(0);

    @Autowired
    private SecurityConfig securityConfig;
    @Autowired
    private ClientHandler clientHandler;

    public void start() {
        PENDING_CONNECTS.incrementAndGet();
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(SHARED_GROUP)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, securityConfig.getConnectOutTime())
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new ClientDecoder());
                            ch.pipeline().addLast(new ClientEncoder());
                            ch.pipeline().addLast(clientHandler);
                        }
                    });

            bootstrap.connect(securityConfig.getFirstIp(), securityConfig.getFirstPort())
                    .addListener((ChannelFutureListener) future -> {
                        PENDING_CONNECTS.decrementAndGet();
                        if (future.isSuccess()) {
                            logger.info("客户端连接成功...");
                            ChannelCache.set(future.channel());
                        } else {
                            logger.error("客户端连接失败: {}", String.valueOf(future.cause()));
                        }
                    });
        } catch (Exception e) {
            PENDING_CONNECTS.decrementAndGet();
            logger.error("连接程序错误: {}", e.getMessage());
        }
    }

    /**
     * 在途（未回调）的 connect 数。
     */
    public static int getPendingConnects() {
        return PENDING_CONNECTS.get();
    }
}
