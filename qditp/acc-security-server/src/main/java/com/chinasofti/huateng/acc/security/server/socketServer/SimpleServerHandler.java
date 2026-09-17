package com.chinasofti.huateng.acc.security.server.socketServer;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.nio.charset.Charset;

/**
 * 处理客户端返回的数据
 *
 * @author Administrator
 */
public class SimpleServerHandler extends ChannelInboundHandlerAdapter {

    /**
     * 读取客户端通道的数据
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ByteBuf) {
            System.out.println(((ByteBuf) msg).toString(Charset.defaultCharset()));
        }


        String result = "hello client ";
        ByteBuf buf = Unpooled.buffer();
        buf.writeBytes(result.getBytes());
        ctx.channel().writeAndFlush(buf);

        ByteBuf buf2 = Unpooled.buffer();
        buf2.writeBytes("\r\n".getBytes());
        ctx.channel().writeAndFlush(buf2);
        System.out.println("==========");
    }

}