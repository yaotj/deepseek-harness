package com.chinasofti.huateng.blacklist.service.hce;

import com.chinasofti.huateng.blacklist.entity.Blacklist;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;


public final class HceBlacklistFileBuilder {

    private static final DateTimeFormatter FILE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private HceBlacklistFileBuilder() {
    }


    public static byte[] build(long version, List<Blacklist> rows) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String now = LocalDateTime.now().format(FILE_TIME_FORMAT);

        write(out, str2Bcd("20"));
        write(out, str2Bcd(now));
        write(out, hexStringToBytes("01"));
        write(out, str2Bcd("1300"));
        write(out, longToByte4Asc(version));
        write(out, str2Bcd(now));

        int count = rows == null ? 0 : rows.size();
        write(out, longToByte4Asc(count));
        if (rows != null) {
            for (Blacklist row : rows) {
                write(out, str2Bcd(cardTypeFlag(row.getCardType())));
                write(out, str2Bcd(row.getCardId()));
                write(out, hexStringToBytes("01"));
                write(out, longToByte4Asc(0L));
            }
        }

        write(out, longToByte4Asc(0L));
        write(out, md5(out.toByteArray()));
        return out.toByteArray();
    }


    private static String cardTypeFlag(String cardType) {
        if (cardType != null && cardType.trim().length() >= 2) {
            return cardType.trim().substring(cardType.trim().length() - 2);
        }
        return "42";
    }

    private static void write(ByteArrayOutputStream out, byte[] bytes) {
        out.write(bytes, 0, bytes.length);
    }

    private static byte[] md5(byte[] content) {
        try {
            return MessageDigest.getInstance("MD5").digest(content);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 不可用", e);
        }
    }

    static byte[] str2Bcd(String asc) {
        if (asc == null || asc.isEmpty()) {
            throw new IllegalArgumentException("BCD 转换输入不能为空");
        }
        if (asc.length() % 2 != 0) {
            asc = "0" + asc;
        }
        byte[] ascii = asc.getBytes(StandardCharsets.US_ASCII);
        byte[] bcd = new byte[ascii.length / 2];
        for (int p = 0; p < bcd.length; p++) {
            int high = bcdValue(ascii[2 * p]);
            int low = bcdValue(ascii[2 * p + 1]);
            bcd[p] = (byte) ((high << 4) + low);
        }
        return bcd;
    }

    private static int bcdValue(byte c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'a' && c <= 'z') {
            return c - 'a' + 10;
        }
        return c - 'A' + 10;
    }

    static byte[] hexStringToBytes(String hex) {
        byte[] bHex = hex.getBytes(StandardCharsets.US_ASCII);
        byte[] b = new byte[bHex.length / 2];
        for (int i = 0; i < b.length; i++) {
            b[i] = (byte) (hexValue(bHex[i * 2]) * 16 + hexValue(bHex[i * 2 + 1]));
        }
        return b;
    }

    private static int hexValue(byte c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'A' && c <= 'F') {
            return c - 'A' + 10;
        }
        return c - 'a' + 10;
    }

    static byte[] longToByte4Asc(long l) {
        byte[] b = new byte[4];
        b[0] = (byte) l;
        b[1] = (byte) (l >>> 8);
        b[2] = (byte) (l >>> 16);
        b[3] = (byte) (l >>> 24);
        return b;
    }
}
