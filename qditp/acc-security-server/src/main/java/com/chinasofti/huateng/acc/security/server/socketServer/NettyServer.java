package com.chinasofti.huateng.acc.security.server.socketServer;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.Delimiters;

/**
 * Netty服务端编写
 *
 * @author Administrator
 */
public class NettyServer {

    public static void main(String[] args) throws InterruptedException {
        ServerBootstrap server = new ServerBootstrap();
        EventLoopGroup parentGroup = new NioEventLoopGroup();
        EventLoopGroup childGroup = new NioEventLoopGroup();
        server.group(parentGroup, childGroup);

        server.option(ChannelOption.SO_BACKLOG, 128);

        server.channel(NioServerSocketChannel.class);

        server.childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addLast(new DelimiterBasedFrameDecoder(Integer.MAX_VALUE, Delimiters.lineDelimiter()[0]));
                ch.pipeline().addLast(new SimpleServerHandler());
            }
        });

        ChannelFuture future = server.bind(8080).sync();
        future.channel().closeFuture().sync();
    }

}