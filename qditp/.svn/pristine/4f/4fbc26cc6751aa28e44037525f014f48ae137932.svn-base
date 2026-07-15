package com.chinasofti.huateng.acc.security.server.reconnect.thread;

import com.chinasofti.huateng.acc.security.server.config.ChannelCache;
import com.chinasofti.huateng.acc.security.server.reconnect.handle.ReConnectManager;
import com.chinasofti.huateng.acc.security.server.socket.SocketClient;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Function:
 *
 * @author crossoverJie
 * Date: 2019-01-20 17:16
 * @since JDK 1.8
 */
@Service
public class ClientHeartBeatHandlerImpl implements HeartBeatHandler {

    private final static Logger LOGGER = LoggerFactory.getLogger(ClientHeartBeatHandlerImpl.class);

    @Autowired
    private SocketClient client;

    @Value("${thread.corePoolSize}")
    private int corePoolSize;

    @Autowired
    private ReConnectManager reConnectManager;

    @Override
    public void process(ChannelHandlerContext ctx) throws Exception {

        LOGGER.info("重连接线程--");
        if (ChannelCache.getSize() < corePoolSize) {
            //重连
            LOGGER.info("当前连接数{}，重连接", ChannelCache.getSize());
            ContextHolder.setReconnect(true);
            client.start();
        } else {
            LOGGER.info("当前连接数达到最大值，不执行重连接操作");
            reConnectManager.reConnectSuccess();
        }
    }

}
