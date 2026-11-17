package com.chinasofti.huateng.acc.es.server.netty.coder;

import com.chinasofti.huateng.acc.es.server.netty.model.Constant;
import com.chinasofti.huateng.acc.es.server.netty.model.MessageBean;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import lombok.extern.slf4j.Slf4j;



@Slf4j
public class ServerDecoder extends LengthFieldBasedFrameDecoder {



    public ServerDecoder(int maxFrameLength, int lengthFieldOffset, int lengthFieldLength, int lengthAdjustment, int initialBytesToStrip, boolean failFast) {
        super(maxFrameLength, lengthFieldOffset, lengthFieldLength, lengthAdjustment, initialBytesToStrip, failFast);
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {

        if (in == null) {
            return null;
        }
        if (in.readableBytes() < Constant.NETTY_LENGTH.MIN_MESSAGE_LENGTH) {
            log.error("字节数不足");
            throw new Exception("字节数不足");
        }

        byte[] dataLength = new byte[4];
        in.readBytes(dataLength);

        byte[] txnType = new byte[4];
        in.readBytes(txnType);


        byte[] nodeId = new byte[8];
        in.readBytes(nodeId);

        byte[] sequence = new byte[9];
        in.readBytes(sequence);

        byte isFileTransaction = in.readByte();

        byte requestType = in.readByte();

        byte md5 = in.readByte();

        byte[] resultType = new byte[2];
        in.readBytes(new byte[2]);

        int bodyLength = Integer.parseInt(new String(dataLength)) - Constant.DataLength.DATA_HEAD_BYTES;
        byte[] dataBody = new byte[bodyLength];
        in.readBytes(dataBody);


        if (bodyLength > 0) {
            return new MessageBean(new String(dataLength), new String(txnType), new String(nodeId), new String(sequence),
                    isFileTransaction, requestType, new String(resultType) , md5,dataBody);
        }
        return null;
    }
}
