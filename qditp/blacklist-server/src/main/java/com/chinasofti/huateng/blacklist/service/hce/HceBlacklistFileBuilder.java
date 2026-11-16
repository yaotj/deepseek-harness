package com.chinasofti.huateng.blacklist.service.hce;

import com.chinasofti.huateng.blacklist.entity.Blacklist;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * HCE 卡黑名单二进制文件生成器，格式对齐既有「NFC卡全量黑名单」（PRM.0013.9700.&lt;版本&gt;）：
 *
 * <pre>
 * 文件头：
 *   BCD("20")                1 字节   文件标识
 *   BCD(yyyyMMddHHmmss)      7 字节   生成时间
 *   Hex("01")                1 字节   更新方式（01 全量）
 *   BCD("1300")              2 字节   参数版本标识
 *   longToByte4Asc(version)  4 字节   文件版本号（小端）
 *   BCD(yyyyMMddHHmmss)      7 字节   生效时间（同生成时间）
 * 记录区：
 *   longToByte4Asc(count)    4 字节   记录数
 *   每条记录：
 *     BCD(票种末两位)        1 字节   0442→"42"，0443→"43"
 *     BCD(cardId)            N 字节   卡号（奇数位左补 0）
 *     Hex("01")              1 字节   黑名单标志
 *     longToByte4Asc(0)      4 字节   保留域
 * 文件尾：
 *   longToByte4Asc(0)        4 字节   保留域
 *   MD5(以上全部字节)        16 字节  校验摘要（原始 digest，非 hex 文本）
 * </pre>
 *
 * <p>注意：参考实现里 MD5 的输入是其内部 ByteBuffer 的 toString（语义已不可考），
 * 本实现取「前面全部字节内容的原始 MD5 digest」。ACC 侧若校验失败，MUST 先核对这个口径。
 */
public final class HceBlacklistFileBuilder {

    private static final DateTimeFormatter FILE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private HceBlacklistFileBuilder() {
    }

    /**
     * 生成黑名单文件内容。
     *
     * @param version 文件版本号
     * @param rows HCE 黑名单记录
     * @return 文件完整字节内容
     */
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

    /**
     * 记录区票种字节：取 CARD_TYPE 末两位（0442→"42"，0443→"43"）。
     * 参考实现里该域写死 "42"；本服务同时覆盖新旧两种 HCE 票种，故按末两位推导，空值兜底 "42"。
     */
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
            // JDK 强制支持 MD5，走不到这里
            throw new IllegalStateException("MD5 不可用", e);
        }
    }

    /** 字符串转 BCD：奇数位左补 0，每两个字符压进一个字节。 */
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

    /** hex 文本转字节数组。 */
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

    /** long 转 4 字节小端序。 */
    static byte[] longToByte4Asc(long l) {
        byte[] b = new byte[4];
        b[0] = (byte) l;
        b[1] = (byte) (l >>> 8);
        b[2] = (byte) (l >>> 16);
        b[3] = (byte) (l >>> 24);
        return b;
    }
}
