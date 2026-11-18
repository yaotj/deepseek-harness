package com.chinasofti.huateng.acc.security.server.socket;

import com.chinasofti.huateng.acc.security.server.config.ChannelCache;
import com.chinasofti.huateng.acc.security.server.config.SecurityConfig;
import com.chinasofti.huateng.acc.security.server.exception.HsmUnavailableException;
import com.chinasofti.huateng.acc.security.server.util.TransformUtils;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class ClientSendMsg {
    private static final Logger log = LoggerFactory.getLogger(ClientSendMsg.class);

    private final SecurityConfig securityConfig;

    public ClientSendMsg(SecurityConfig securityConfig) {
        this.securityConfig = securityConfig;
    }

    public Channel sendMsg(Object msg, String cardNo, String userRetain) {
        if (msg == null) {
            log.error("send msg is null");
        }

        Channel channel = ChannelCache.get();
        ChannelCache.touch(channel);
        channel.writeAndFlush(msg);
        log.info("channelId {} cardNo {} userRetain {} send {}", channel.id(), cardNo, userRetain,
                TransformUtils.bytesToHex((byte[]) msg));
        return channel;
    }

    public Channel sendCertificateMsg(Object msg, String userRetain) {
        if (msg == null) {
            log.error("send certificate msg is null");
        }

        Channel channel = ChannelCache.get();
        ChannelCache.touch(channel);
        channel.writeAndFlush(msg);
        log.info("channelId {} userRetain {} send {}", channel.id(), userRetain,
                TransformUtils.bytesToHex((byte[]) msg));
        return channel;
    }

    public RawSocketResponse sendRawMsg(byte[] msg, RawResponseSpec responseSpec) {
        if (msg == null) {
            throw new IllegalArgumentException("msg can not be null");
        }

        Channel channel = null;
        try {
            channel = ChannelCache.acquireExclusive(securityConfig.getAcquireTimeoutMs());
            RawSocketResponseFuture future = new RawSocketResponseFuture();
            channel.attr(ClientDecoder.RAW_REQUEST_CONTEXT).set(new RawRequestContext(responseSpec, future));
            channel.writeAndFlush(msg).sync();
            ChannelCache.touch(channel);
            log.info("channelId {} send raw msg {}", channel.id(), TransformUtils.bytesToHex(msg));
            return future.get(securityConfig.getReadOutTime(), TimeUnit.MILLISECONDS);
        } catch (HsmUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("send raw message failed", e);
        } finally {
            if (channel != null) {
                channel.attr(ClientDecoder.RAW_REQUEST_CONTEXT).set(null);
                ChannelCache.releaseExclusive(channel);
            }
        }
    }

    /**
     * 在<b>指定的那一条</b>连接上发原始报文并等应答（独占该连接直到应答返回或超时）。
     *
     * <p>与 {@link #sendRawMsg(byte[], RawResponseSpec)} 的唯一区别是「不另取连接」：
     * 后者由池按游标挑一条空闲连接，无法定位到调用方正在遍历的连接。空闲心跳必须
     * 逐条保活，因此单独开这条入口。</p>
     *
     * <p>拿不到该连接（已被业务独占 / 已失活 / 不在池内）时返回 <b>null</b>，不阻塞、
     * 不抛 {@link HsmUnavailableException}——心跳跳过这条即可，下一轮再判。</p>
     */
    public RawSocketResponse sendRawMsgOn(Channel channel, byte[] msg, RawResponseSpec responseSpec) {
        if (msg == null) {
            throw new IllegalArgumentException("msg can not be null");
        }
        if (!ChannelCache.tryAcquireExclusive(channel)) {
            return null;
        }
        try {
            RawSocketResponseFuture future = new RawSocketResponseFuture();
            channel.attr(ClientDecoder.RAW_REQUEST_CONTEXT).set(new RawRequestContext(responseSpec, future));
            channel.writeAndFlush(msg).sync();
            ChannelCache.touch(channel);
            log.info("channelId {} send raw msg {}", channel.id(), TransformUtils.bytesToHex(msg));
            return future.get(securityConfig.getReadOutTime(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("send raw message failed on channel " + channel.id(), e);
        } finally {
            channel.attr(ClientDecoder.RAW_REQUEST_CONTEXT).set(null);
            ChannelCache.releaseExclusive(channel);
        }
    }
}
