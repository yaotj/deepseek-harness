package com.chinasofti.huateng.common.util;

public class ByteConvert {
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

    public static String getTicketPrice(int price) {
        String ticketPrice = Integer.toHexString(price);
        return "00000000".substring(0, 8 - ticketPrice.length()) + ticketPrice;
    }

    public static String bytesToHexString(byte[] src) {
        if (src == null || src.length == 0) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (byte b : src) {
            String hex = Integer.toHexString(b & 0xFF);
            if (hex.length() < 2) {
                builder.append('0');
            }
            builder.append(hex);
        }
        return builder.toString();
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
