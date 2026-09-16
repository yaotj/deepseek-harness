package com.chinasofti.huateng.para.util;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 参数文件读取工具。
 */
public final class ParaFileReadUtils {

    private static final Charset GB2312 = Charset.forName("GB2312");

    private ParaFileReadUtils() {
    }

    public static int byteToInt(byte b) {
        return b & 0xFF;
    }

    public static int twoBytesToIntLittle(byte[] bytes) {
        if (bytes.length != 2) {
            throw new IllegalArgumentException("Input must be 2 bytes long");
        }
        return ((bytes[1] & 0xFF) << 8) | (bytes[0] & 0xFF);
    }

    public static long fourBytesToIntLittle(byte[] bytes) {
        if (bytes.length != 4) {
            throw new IllegalArgumentException("Input must be 4 bytes long");
        }
        long result = 0;
        result |= (bytes[0] & 0xFFL);
        result |= ((bytes[1] & 0xFFL) << 8);
        result |= ((bytes[2] & 0xFFL) << 16);
        result |= ((bytes[3] & 0xFFL) << 24);
        return result;
    }

    public static String bcdToString(byte[] bcd) {
        StringBuilder result = new StringBuilder();
        for (byte b : bcd) {
            int high = (b & 0xF0) >> 4;
            int low = b & 0x0F;
            result.append(high).append(low);
        }
        return result.toString().replaceFirst("^0+", "");
    }

    public static String bcdToStringKeepLeadingZero(byte[] bcd) {
        StringBuilder result = new StringBuilder();
        for (byte b : bcd) {
            int high = (b & 0xF0) >> 4;
            int low = b & 0x0F;
            result.append(high).append(low);
        }
        return result.toString();
    }

    public static String bcdToHexString(byte[] bcd) {
        StringBuilder result = new StringBuilder();
        for (byte b : bcd) {
            int high = (b & 0xF0) >> 4;
            int low = b & 0x0F;
            result.append(Integer.toHexString(high)).append(Integer.toHexString(low));
        }
        return result.toString();
    }

    public static String readGb2312(byte[] bytes) {
        String result = new String(bytes, GB2312);
        if (!result.matches("^[0-9]+$")) {
            result = result.replaceAll("\\s+$", "");
        }
        if (result.matches("^[0-9]+$")) {
            result = result.replaceFirst("^0+", "");
        }
        return result.isEmpty() ? "0" : result.trim();
    }

    public static String byteToBinary(byte b) {
        StringBuilder result = new StringBuilder(8);
        for (int i = 7; i >= 0; i--) {
            result.append((b >> i) & 1);
        }
        return result.reverse().toString();
    }

    public static String leftPad(String value, int length, char padChar) {
        if (value == null) {
            value = "";
        }
        if (value.length() >= length) {
            return value;
        }
        return String.valueOf(padChar).repeat(length - value.length()) + value;
    }

    /**
     * 计算指定区间的 MD5 十六进制串（小写）。
     *
     * <p>参数文件的 MD5 约定是「除尾部 16 字节校验值外的全文」，即 md5Hex(bytes, 0, len - 16)，
     * 结果就是 TBL_PARA_VERSION.MD5_VALUE 存的值。</p>
     *
     * <p>⚠️ RowNetworkParser / CalendarParser / TicketParser / RateParser 各有一份**算法完全相同的
     * 私有 md5Hex**（历史实现，未合并）。ParaFileImportService 的「版本号 + MD5」预筛依赖
     * 本方法与那四份的结果**逐字节一致**——改动任意一处 MUST 同步改其余四处，
     * 否则内容相同的文件会被误判成有变更，每轮扫描都重复入库。</p>
     */
    public static String md5Hex(byte[] bytes, int offset, int length) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            md5.update(bytes, offset, length);
            byte[] digest = md5.digest();
            StringBuilder result = new StringBuilder();
            for (byte b : digest) {
                result.append(String.format("%02x", b));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 algorithm not found", e);
        }
    }
}
