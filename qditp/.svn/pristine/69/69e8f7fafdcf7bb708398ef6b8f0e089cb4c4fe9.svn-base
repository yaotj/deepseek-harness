package com.chinasofti.huateng.acc.security.server.socket;

import com.chinasofti.huateng.acc.security.server.config.ChannelCache;
import com.chinasofti.huateng.acc.security.server.config.SecurityConfig;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Description:
 *
 * @author houkepan
 * @date 2019/2/18 15:01
 */
@Component
public class SocketClient {
    private static final Logger logger = LoggerFactory.getLogger(SocketClient.class);

    @Autowired
    private SecurityConfig securityConfig;
    @Autowired
    private ClientHandler clientHandler;

    @Async("socketAsyncExecutor")
    public void start() {
        EventLoopGroup group = new NioEventLoopGroup();
        Bootstrap bootstrap = new Bootstrap();
        ChannelFuture future = null;
        try {
            bootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) throws Exception {
                            ch.pipeline().addLast(new ClientDecoder());
                            ch.pipeline().addLast(new ClientEncoder());
                            ch.pipeline().addLast(clientHandler);
                        }
                    });

            future = bootstrap.connect(securityConfig.getFirstIp(), securityConfig.getFirstPort()).sync();
            // 判断是否连接成功
            if (future.isSuccess()) {
                logger.info("客户端连接成功...");
                ChannelCache.set(future.channel());
                // 等待客户端链路关闭，就是由于这里会将线程阻塞，导致无法发送信息，这里开了线程
                future.channel().closeFuture().sync();
            } else {
                logger.error("客户端连接失败...");
            }

        } catch (Exception e) {
            logger.error("连接程序错误" + e.getMessage());
        } finally {
            logger.info("客户端程序关闭");
            group.shutdownGracefully();
            if (future != null) {
                ChannelCache.clear(future.channel());
            }
        }

    }

}
