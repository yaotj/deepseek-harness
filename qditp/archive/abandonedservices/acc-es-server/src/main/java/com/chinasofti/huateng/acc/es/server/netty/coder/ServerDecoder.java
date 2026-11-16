package com.chinasofti.huateng.acc.es.server.netty.coder;

import com.chinasofti.huateng.acc.es.server.netty.model.Constant;
import com.chinasofti.huateng.acc.es.server.netty.model.MessageBean;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import lombok.extern.slf4j.Slf4j;


/**
 * 解码器
 */
@Slf4j
public class ServerDecoder extends LengthFieldBasedFrameDecoder {

    /**
     * @param byteOrder           大小端  ByteOrder.LITTLE_ENDIAN
     * @param maxFrameLength      帧的最大长度
     * @param lengthFieldOffset   定义长度域位于发送的字节数组中的下标。换句话说：发送的字节数组中下标为${lengthFieldOffset}的地方是长度域的开始地方
     * @param lengthFieldLength   length字段所占的字节长（如数组的length=12,那么lengthFieldLength的长度就为2）
     * @param lengthAdjustment    修改帧数据长度字段中定义的值，可以为负数 因为有时候我们习惯把头部记入长度,若为负数,则说明要推后多少个字段
     *                            lengthAdjustment  = 数据包长度 - lengthFieldOffset - lengthFieldLength  - 长度域的值(满足发送条件)
     * @param initialBytesToStrip 解析时候跳过多少个长度
     * @param failFast            默认为true，当frame长度超过maxFrameLength时立即报TooLongFrameException异常，为false，读取完整个帧再报异
     */

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
