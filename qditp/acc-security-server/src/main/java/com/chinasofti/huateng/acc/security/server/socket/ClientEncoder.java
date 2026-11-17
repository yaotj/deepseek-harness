package com.chinasofti.huateng.acc.security.server.socket;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;


public class ClientEncoder extends MessageToByteEncoder<byte[]> {

    @Override
    protected void encode(ChannelHandlerContext ctx, byte[] msg, ByteBuf out) throws Exception {
        if (msg == null) {
            throw new Exception("msg is null");
        }
        out.writeBytes(msg);
    }
}
