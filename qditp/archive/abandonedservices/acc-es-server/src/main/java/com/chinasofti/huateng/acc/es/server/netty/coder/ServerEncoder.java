package com.chinasofti.huateng.acc.es.server.netty.coder;

import com.chinasofti.huateng.acc.es.server.netty.model.MessageBean;
import com.chinasofti.huateng.acc.es.server.netty.model.Messagehead;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * 编码器
 */

public class ServerEncoder extends MessageToByteEncoder<Messagehead> {

    @Override
    protected void encode(ChannelHandlerContext ctx, Messagehead msg, ByteBuf out) throws Exception {
        if (msg == null) {
            throw new Exception("msg is null");
        }
        out.writeBytes(msg.getDataLength().getBytes());
        out.writeBytes(msg.getTxnType().getBytes());
        out.writeBytes(msg.getNodeId().getBytes());
        out.writeBytes(msg.getSequence().getBytes());
        out.writeByte(msg.getIsFileTransaction());
        out.writeByte(msg.getRequestType());
        out.writeByte(msg.getMd5());
        out.writeBytes(msg.getMack().getBytes());
        if (msg instanceof MessageBean) {
            byte[] a = ((MessageBean) msg).getDataBody();
            out.writeBytes(a);
        }
    }
}
