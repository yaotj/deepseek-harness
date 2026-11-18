package com.chinasofti.huateng.acc.security.server.reconnect.thread;

import com.chinasofti.huateng.acc.security.server.config.ChannelCache;
import com.chinasofti.huateng.acc.security.server.config.SecurityConfig;
import com.chinasofti.huateng.acc.security.server.reconnect.handle.ReConnectManager;
import com.chinasofti.huateng.acc.security.server.socket.SocketClient;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 池补齐任务：目标连接数 = security.channelPoolSize（与 socket 线程池解耦），
 * 缺口 = 目标 - 存活 - 在途 connect，按缺口一次性补齐，补满即停重连任务。
 */
@Service
public class ClientHeartBeatHandlerImpl implements HeartBeatHandler {

    private final static Logger LOGGER = LoggerFactory.getLogger(ClientHeartBeatHandlerImpl.class);

    @Autowired
    private SocketClient client;

    @Autowired
    private SecurityConfig securityConfig;

    @Autowired
    private ReConnectManager reConnectManager;

    @Override
    public void process(ChannelHandlerContext ctx) {
        int target = securityConfig.getChannelPoolSize();
        int current = ChannelCache.getSize();
        int pending = SocketClient.getPendingConnects();
        int missing = target - current - pending;

        if (missing > 0) {
            LOGGER.info("连接池补齐：目标{}，存活{}，在途{}，本次补{}条", target, current, pending, missing);
            ContextHolder.setReconnect(true);
            for (int i = 0; i < missing; i++) {
                client.start();
            }
        } else {
            LOGGER.debug("连接池已满（目标{}，存活{}，在途{}），停止重连任务", target, current, pending);
            reConnectManager.reConnectSuccess();
        }
    }

}
