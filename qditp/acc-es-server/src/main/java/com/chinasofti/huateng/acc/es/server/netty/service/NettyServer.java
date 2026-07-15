package com.chinasofti.huateng.acc.es.server.netty.service;

import com.chinasofti.huateng.acc.es.server.netty.coder.ServerDecoder;
import com.chinasofti.huateng.acc.es.server.netty.coder.ServerEncoder;
import com.chinasofti.huateng.acc.es.server.netty.model.Constant;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

/**
 * 服务端基本配置，通过一个静态单例类，保证启动时候只被加载一次
 *
 * @author fanchi
 */
@Slf4j
@Component
public class NettyServer {


    @Value("${netty.port}")
    private  int port;

    @Autowired
    private NettyServerHandler serverHandler;

    /**
     * 用于处理服务器端接收客户端连接
     *
     */
    private EventLoopGroup bossGroup;
    /**
     * 进行网络通信（读写）
     */
    private EventLoopGroup workerGroup;
    /**
     * 辅助工具类，用于服务器通道的一系列配置
     */
    private ServerBootstrap serverBootstrap;

    private  ChannelFuture channelFuture;

    public NettyServer(){
       bossGroup = new NioEventLoopGroup(1);
       workerGroup = new NioEventLoopGroup();
            serverBootstrap = new ServerBootstrap();
            //绑定两个线程组
            serverBootstrap.group(bossGroup,workerGroup)
                    //指定NIO的模式
                    .channel(NioServerSocketChannel.class)
                    //配置具体的数据处理方式
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                                      @Override
                                      protected void initChannel(SocketChannel ch) throws Exception {
                                          ChannelPipeline pipeline = ch.pipeline();
                                          pipeline.addLast(new ServerEncoder());
                                          pipeline.addLast(new ServerDecoder(
                                                  Constant.NETTY_LENGTH.MAX_MESSAGE_LENGTH,
                                                  Constant.NETTY_LENGTH.LENGTH_FIELD_OFFSET,
                                                  Constant.NETTY_LENGTH.LENGTH_FIELD_LENGTH,
                                                  Constant.NETTY_LENGTH.LENGTH_ADJUSTMENT,
                                                  Constant.NETTY_LENGTH.INITIAL_BYTES_TO_STRIP, false));
                                          pipeline.addLast(serverHandler); //业务处理器
                                      }
                                  }
                    ).option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);


    }

    public void start(){
        try {
            channelFuture = serverBootstrap.bind(port).sync();
            if (channelFuture.isSuccess()) {
                log.info("Netty Server Start successful");
                log.info("服务提供方开始提供服务~~");
            } else {
                log.error("Netty Server failed");
            }
            // 等待服务监听端口关闭
            channelFuture.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            log.error("服务端异常");
            e.printStackTrace();
        }
    }
    /**
     * 关闭服务器方法
     */
    @PreDestroy
    public void close() {
        log.info("程序关闭，关闭服务端....");
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
    }

}

