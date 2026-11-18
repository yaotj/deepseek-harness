package com.chinasofti.huateng.acc.security.server.socket;

import com.chinasofti.huateng.acc.security.server.config.ChannelCache;
import com.chinasofti.huateng.acc.security.server.config.SecurityConfig;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 加密机空闲连接心跳保活。
 *
 * <p>使用 SJJ1312-B 程序员手册的 0x00「请求返回系统信息」指令
 * （应答：'A' + 版本4 + 协议4 + 修订日期8），对空闲超过阈值的连接保活，
 * 避免设备/网络侧空闲回收造成的周期性断连抖动。</p>
 *
 * <p>池繁忙时（该连接正被业务独占）跳过这一条、继续看下一条——有真实流量本就不需要心跳。</p>
 */
@Component
public class HsmHeartbeatScheduler {
    private static final Logger log = LoggerFactory.getLogger(HsmHeartbeatScheduler.class);

    /** 0x00 请求返回系统信息：版本(4) + 协议(4) + 修订日期(8)。 */
    private static final byte[] SYS_INFO_REQUEST = {0x00};
    private static final RawResponseSpec SYS_INFO_SPEC = new RawResponseSpec(false, List.of(
            RawResponseFieldSpec.fixed(4),
            RawResponseFieldSpec.fixed(4),
            RawResponseFieldSpec.fixed(8)));

    private final ClientSendMsg clientSendMsg;
    private final SecurityConfig securityConfig;

    public HsmHeartbeatScheduler(ClientSendMsg clientSendMsg, SecurityConfig securityConfig) {
        this.clientSendMsg = clientSendMsg;
        this.securityConfig = securityConfig;
    }

    @Scheduled(initialDelayString = "${security.heartbeatPeriodMs:30000}",
            fixedDelayString = "${security.heartbeatPeriodMs:30000}")
    public void heartbeat() {
        if (!securityConfig.isHeartbeatEnabled()) {
            return;
        }
        long idleThresholdMs = securityConfig.getHeartbeatIdleSeconds() * 1000L;
        long now = System.currentTimeMillis();
        for (Channel channel : ChannelCache.snapshot()) {
            if (!channel.isActive()) {
                continue;
            }
            if (now - ChannelCache.getLastActive(channel) < idleThresholdMs) {
                continue;
            }
            try {
                // MUST 在「这条」连接上发：sendRawMsg 会另从池里取一条，心跳就打不到正在遍历的连接上
                RawSocketResponse response = clientSendMsg.sendRawMsgOn(channel, SYS_INFO_REQUEST, SYS_INFO_SPEC);
                if (response == null) {
                    // 该连接正被业务独占，本轮跳过，下一轮再判
                    continue;
                }
                log.info("hsm heartbeat ok, channelId {} ansCode {}", channel.id(), response.ansCode());
            } catch (Exception e) {
                log.warn("hsm heartbeat failed on channel {}, closing it: {}", channel.id(), e.getMessage());
                ChannelCache.clear(channel);
            }
        }
    }
}
