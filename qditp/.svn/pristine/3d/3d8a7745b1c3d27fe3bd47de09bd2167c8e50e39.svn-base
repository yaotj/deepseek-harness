package com.chinasofti.huateng.para.util;

import java.nio.charset.Charset;

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
}
