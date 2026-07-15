package com.chinasofti.huateng.acc.security.server.socket;

import com.chinasofti.huateng.acc.security.server.config.ChannelCache;
import com.chinasofti.huateng.acc.security.server.config.LocalCache;
import com.chinasofti.huateng.acc.security.server.reconnect.handle.ReConnectManager;
import com.chinasofti.huateng.acc.security.server.reconnect.util.SpringBeanFactory;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ScheduledExecutorService;

@Component
@ChannelHandler.Sharable
public class ClientHandler extends ChannelInboundHandlerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(ClientHandler.class);

    private ScheduledExecutorService scheduledExecutorService;
    private ReConnectManager reConnectManager;

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof RawSocketResponse rawSocketResponse) {
            RawRequestContext rawRequestContext = ctx.channel().attr(ClientDecoder.RAW_REQUEST_CONTEXT).getAndSet(null);
            if (rawRequestContext != null) {
                rawRequestContext.getFuture().complete(rawSocketResponse);
            }
            return;
        }
        if (msg instanceof Object) {
            RawRequestContext rawRequestContext = ctx.channel().attr(ClientDecoder.RAW_REQUEST_CONTEXT).get();
            if (rawRequestContext != null) {
                rawRequestContext.getFuture().completeExceptionally(
                        new IllegalStateException("unexpected raw socket message type: " + msg.getClass().getName()));
                ctx.channel().attr(ClientDecoder.RAW_REQUEST_CONTEXT).set(null);
            }
        }

        logger.debug("receive socket response {}", msg);
        String str = (String) msg;
        String userRetain = str.substring(2, 18);
        logger.info("channelId {} userRetain {} socket response {}", ctx.channel().id(), userRetain, str);
        LocalCache.set(ctx.channel().id() + userRetain.toUpperCase(), str);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Channel channel = ctx.channel();
        logger.info("socket disconnected {}", channel.remoteAddress());
        super.channelInactive(ctx);
        ChannelCache.clear(channel);

        if (scheduledExecutorService == null) {
            scheduledExecutorService = SpringBeanFactory.getBean("scheduledTask", ScheduledExecutorService.class);
            reConnectManager = SpringBeanFactory.getBean(ReConnectManager.class);
        }
        logger.info("socket disconnected, reconnect");
        reConnectManager.reConnect(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("socket exception", cause);
        RawRequestContext rawRequestContext = ctx.channel().attr(ClientDecoder.RAW_REQUEST_CONTEXT).getAndSet(null);
        if (rawRequestContext != null) {
            rawRequestContext.getFuture().completeExceptionally(cause);
        }
        ChannelCache.clear(ctx.channel());
    }
}
