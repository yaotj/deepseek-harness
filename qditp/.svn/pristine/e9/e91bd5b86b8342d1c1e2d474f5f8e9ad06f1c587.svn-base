package com.chinasofti.huateng.acc.security.server.shortconnect;

import com.chinasofti.huateng.acc.security.server.config.SecurityConfig;
import com.chinasofti.huateng.acc.security.server.util.TransformUtils;
import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Description:
 *
 * @author houkepan
 * @date 2019/2/18 15:01
 */
@Component
public class ShortConnectClient {
    private static final Logger logger = LoggerFactory.getLogger(ShortConnectClient.class);

    @Autowired
    private SecurityConfig securityConfig;

    public ResultVO<Boolean> getTacResult(byte[] param, String userReatin) {

        EventLoopGroup group = new NioEventLoopGroup();
        Bootstrap bootstrap = new Bootstrap();
        try {
            bootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) throws Exception {
                            ch.pipeline().addLast(new ShortConnectClientEncoder());
                            ch.pipeline().addLast(new ShortConnectClientHandler());
                        }
                    });

            ChannelFuture future = bootstrap.connect(securityConfig.getFirstIp(), securityConfig.getFirstPort()).sync();
            // 判断是否连接成功
            if (future.isSuccess()) {
                logger.info("客户端连接成功...");
            } else {
                logger.error("客户端连接失败...");
            }

            // 发送数据
            future.channel().writeAndFlush(param);

            // 等待客户端链路关闭，就是由于这里会将线程阻塞，导致无法发送信息，所以我这里开了线程
            future.channel().closeFuture().sync();

            //接收服务端返回的数据
            AttributeKey<String> key = AttributeKey.valueOf(userReatin.toUpperCase());
            String resultData = future.channel().attr(key).get();
            logger.info(future.channel().id() + "加密机验证结果【" + resultData + "】" + TransformUtils.bytesToHex(param));

            ResultVO<Boolean> resultVO = new ResultVO();

            if ("41".equals(resultData.substring(0, 2))) {
                resultVO.setData(true);
            } else {
                resultVO.setData(false);
            }
            return resultVO;
        } catch (Exception e) {
            logger.error("连接程序错误" + e.getMessage());
            return ResultMapper.error();
        } finally {
            logger.info("客户端程序关闭");
            group.shutdownGracefully();
        }

    }

}
