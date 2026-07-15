package com.chinasofti.huateng.common.util;

public class ByteConvertUtil {
    protected static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

    public static byte[] byteMergerAll(byte[]... values) {
        int length = 0;
        for (byte[] value : values) {
            length += value.length;
        }
        byte[] result = new byte[length];
        int offset = 0;
        for (byte[] value : values) {
            System.arraycopy(value, 0, result, offset, value.length);
            offset += value.length;
        }
        return result;
    }

    public static byte[] stringToBcd(String str) {
        int length = str.length();
        byte[] ascii = str.getBytes();
        byte[] bcd = new byte[length / 2];
        int index = 0;
        for (int i = 0; i < (length + 1) / 2; i++) {
            bcd[i] = ascToBcd(ascii[index++]);
            bcd[i] = (byte) (((index >= length) ? 0x00 : ascToBcd(ascii[index++])) + (bcd[i] << 4));
        }
        return bcd;
    }

    public static String strWithLen(String val, int len) {
        if (val == null || val.isEmpty()) {
            return "0".repeat(len);
        }
        if (val.length() == len) {
            return val;
        }
        if (val.length() < len) {
            return "0".repeat(len - val.length()) + val;
        }
        return val.substring(val.length() - len);
    }

    public static String getCharString(byte[] data, int offset, int length) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < length; i++) {
            builder.append((char) data[offset + i]);
        }
        return builder.toString();
    }

    public static String getBcdString(byte[] data, int offset, int length) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < length; i++) {
            builder.append(byteToBcd(data[offset + i]));
        }
        return builder.toString();
    }

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xff;
            hexChars[i * 2] = HEX_ARRAY[value >>> 4];
            hexChars[i * 2 + 1] = HEX_ARRAY[value & 0x0f];
        }
        return new String(hexChars);
    }

    public static String byteToHexString(byte value) {
        int v = value & 0xff;
        return new String(new char[]{HEX_ARRAY[v >>> 4], HEX_ARRAY[v & 0x0f]});
    }

    private static byte ascToBcd(byte asc) {
        if (asc >= '0' && asc <= '9') {
            return (byte) (asc - '0');
        }
        if (asc >= 'A' && asc <= 'F') {
            return (byte) (asc - 'A' + 10);
        }
        if (asc >= 'a' && asc <= 'f') {
            return (byte) (asc - 'a' + 10);
        }
        return (byte) (asc - 48);
    }

    private static String byteToBcd(byte value) {
        int number = value < 0 ? 256 + value : value;
        return Integer.toString(number / 16) + Integer.toString(number % 16);
    }
}
