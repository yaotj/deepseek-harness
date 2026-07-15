package com.chinasofti.huateng.acc.security.server.shortconnect;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * Description:
 *
 * @author houkepan
 * @date 2019/2/18 15:03
 */
public class ShortConnectClientEncoder extends MessageToByteEncoder<byte[]> {

    @Override
    protected void encode(ChannelHandlerContext ctx, byte[] msg, ByteBuf out) throws Exception {
        if (msg == null) {
            throw new Exception("msg is null");
        }
        out.writeBytes(msg);
    }
}
