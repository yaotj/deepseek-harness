package com.chinasofti.huateng.acc.security.server.itp.util;

public final class ItpHexUtils {
    private ItpHexUtils() {
    }

    public static byte[] hexStringToByte(String hex) {
        String value = hex.toUpperCase();
        if ((value.length() & 1) == 1) {
            throw new UnsupportedOperationException("length is odd number");
        }
        byte[] result = new byte[value.length() / 2];
        char[] chars = value.toCharArray();
        for (int i = 0; i < result.length; i++) {
            int pos = i << 1;
            result[i] = (byte) ((toByte(chars[pos]) << 4) | toByte(chars[pos + 1]));
        }
        return result;
    }

    private static int toByte(char c) {
        int value = "0123456789ABCDEF".indexOf(c);
        if (value < 0) {
            throw new NumberFormatException("Do not support conversion to byte for '" + c + "'");
        }
        return value & 0x0F;
    }
}
