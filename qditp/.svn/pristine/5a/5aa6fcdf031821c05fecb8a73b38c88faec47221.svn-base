package com.chinasofti.huateng.acc.security.server.shortconnect;

import com.chinasofti.huateng.acc.security.server.util.TransformUtils;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 处理服务端返回的数据
 */
public class ShortConnectClientHandler extends ChannelInboundHandlerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(ShortConnectClientHandler.class);

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {

        if (msg instanceof ByteBuf) {
            ByteBuf buff = (ByteBuf) msg;
            try {
                byte[] bytes = new byte[buff.readableBytes()];
                buff.readBytes(bytes);

                String str = TransformUtils.bytesToHex(bytes);
                String userRetain = str.substring(2, 18);
                logger.debug(ctx.channel().id() + "用户保留字 " + userRetain + "加密机返回数据" + str);

                AttributeKey<String> key = AttributeKey.valueOf(userRetain.toUpperCase());
                ctx.channel().attr(key).set(TransformUtils.bytesToHex(bytes));

            } catch (Exception e) {
                logger.error(e.getMessage());
            } finally {
                //手动释放
                ReferenceCountUtil.release(buff);
            }
        }

        //把客户端的通道关闭
        ctx.close();
    }


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("服务异常，主动关闭连接");
        ctx.close();
    }
}