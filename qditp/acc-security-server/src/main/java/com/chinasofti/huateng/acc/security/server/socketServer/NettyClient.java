package com.chinasofti.huateng.acc.security.server.socketServer;

import com.chinasofti.huateng.acc.security.server.shortconnect.ShortConnectClientHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.Delimiters;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.AttributeKey;

/**
 * Netty客户端编写
 *
 * @author Administrator
 */
public class NettyClient {

    public static void main(String[] args) throws InterruptedException {
        Bootstrap client = new Bootstrap();

        EventLoopGroup group = new NioEventLoopGroup();
        client.group(group);

        client.channel(NioSocketChannel.class);

        client.handler(new ChannelInitializer<NioSocketChannel>() {  //通道是NioSocketChannel
            @Override
            protected void initChannel(NioSocketChannel ch) throws Exception {
                ch.pipeline().addLast(new StringEncoder());
                ch.pipeline().addLast(new DelimiterBasedFrameDecoder(
                        Integer.MAX_VALUE, Delimiters.lineDelimiter()[0]));
                ch.pipeline().addLast(new ShortConnectClientHandler());
            }
        });

        ChannelFuture future = client.connect("localhost", 8080).sync();

        future.channel().writeAndFlush("测试" + "\r\n");

        for (int i = 0; i < 5; i++) {
            String msg = "ssss" + i + "\r\n";
            future.channel().writeAndFlush(msg);
        }

        future.channel().closeFuture().sync();

        AttributeKey<String> key = AttributeKey.valueOf("ServerData");
        Object result = future.channel().attr(key).get();
        System.out.println(result.toString());

        group.shutdownGracefully();

    }

}