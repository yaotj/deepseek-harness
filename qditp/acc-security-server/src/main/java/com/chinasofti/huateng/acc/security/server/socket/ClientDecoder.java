package com.chinasofti.huateng.acc.security.server.socket;

import com.chinasofti.huateng.acc.security.server.config.Constant;
import com.chinasofti.huateng.acc.security.server.util.TransformUtils;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ClientDecoder extends ByteToMessageDecoder {
    private static final Logger log = LoggerFactory.getLogger(ClientDecoder.class);

    private static final int HEAD_LENGTH = 8;

    public static final AttributeKey<RawRequestContext> RAW_REQUEST_CONTEXT =
            AttributeKey.valueOf("rawRequestContext");

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        Channel channel = ctx.channel();
        RawRequestContext rawRequestContext = channel.attr(RAW_REQUEST_CONTEXT).get();
        if (rawRequestContext != null) {
            RawSocketResponse response = decodeRaw(in, rawRequestContext.getResponseSpec());
            if (response != null) {
                out.add(response);
            }
            return;
        }

        if (in.readableBytes() < HEAD_LENGTH) {
            return;
        }

        byte type = in.readByte();
        if (type == 65) {
            byte[] body = new byte[8];
            in.readBytes(body);
            if (body[0] == TransformUtils.HexStringToByteArr(Constant.ORDER_TYPE.BYTE8)[0]) {
                byte[] body2 = new byte[8];
                in.readBytes(body2);
                out.add("41" + TransformUtils.bytesToHex(body) + TransformUtils.bytesToHex(body2));
            } else if (body[0] == TransformUtils.HexStringToByteArr(Constant.ORDER_TYPE.BYTE10)[0]) {
                byte[] body2 = new byte[10];
                in.readBytes(body2);
                out.add("41" + TransformUtils.bytesToHex(body) + TransformUtils.bytesToHex(body2));
            } else {
                out.add("41" + TransformUtils.bytesToHex(body));
            }
        } else if (type == 69) {
            byte[] body = new byte[9];
            in.readBytes(body);
            out.add("45" + TransformUtils.bytesToHex(body));
        } else {
            log.error("unexpected socket response type {}, readable {}", type, in.readableBytes());
        }
    }

    private RawSocketResponse decodeRaw(ByteBuf in, RawResponseSpec responseSpec) {
        if (in.readableBytes() < 1) {
            return null;
        }
        in.markReaderIndex();
        byte ansCode = in.readByte();
        byte[] reserved = null;
        if (responseSpec.reserved()) {
            if (in.readableBytes() < 8) {
                in.resetReaderIndex();
                return null;
            }
            reserved = new byte[8];
            in.readBytes(reserved);
        }

        if (ansCode == 'A') {
            java.util.List<byte[]> fields = new java.util.ArrayList<>(responseSpec.fields().size());
            for (RawResponseFieldSpec fieldSpec : responseSpec.fields()) {
                int fieldLength = fieldSpec.fixedLength();
                if (fieldSpec.lengthFromIndex() >= 0) {
                    fieldLength = byteArrayToInt(fields.get(fieldSpec.lengthFromIndex()));
                }
                if (in.readableBytes() < fieldLength) {
                    in.resetReaderIndex();
                    return null;
                }
                byte[] field = new byte[fieldLength];
                in.readBytes(field);
                fields.add(field);
            }
            return new RawSocketResponse(ansCode, null, reserved, fields);
        }

        if (in.readableBytes() < 1) {
            in.resetReaderIndex();
            return null;
        }
        byte[] errCode = new byte[1];
        in.readBytes(errCode);
        return new RawSocketResponse(ansCode, errCode, reserved, java.util.List.of());
    }

    private int byteArrayToInt(byte[] value) {
        int result = 0;
        for (byte b : value) {
            result <<= 8;
            result |= b & 0xFF;
        }
        return result;
    }
}
