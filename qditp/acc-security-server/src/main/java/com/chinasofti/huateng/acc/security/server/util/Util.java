package com.chinasofti.huateng.acc.security.server.util;

import java.math.BigInteger;

public class Util {

    private static final char[] DIGITS_LOWER = {'0', '1', '2', '3', '4', '5',
            '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    private static final char[] DIGITS_UPPER = {'0', '1', '2', '3', '4', '5',
            '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    private static final String[] HEX_TO_BINARY = {"0000", "0001", "0010", "0011",
            "0100", "0101", "0110", "0111", "1000", "1001", "1010", "1011",
            "1100", "1101", "1110", "1111"};


    public static byte[] intToBytes(int num) {
        byte[] bytes = new byte[4];
        bytes[0] = (byte) (0xff & (num >> 0));
        bytes[1] = (byte) (0xff & (num >> 8));
        bytes[2] = (byte) (0xff & (num >> 16));
        bytes[3] = (byte) (0xff & (num >> 24));
        return bytes;
    }


    public static int byteToInt(byte[] bytes) {
        int num = 0;
        int temp;
        temp = (0x000000ff & (bytes[0])) << 0;
        num = num | temp;
        temp = (0x000000ff & (bytes[1])) << 8;
        num = num | temp;
        temp = (0x000000ff & (bytes[2])) << 16;
        num = num | temp;
        temp = (0x000000ff & (bytes[3])) << 24;
        num = num | temp;
        return num;
    }


    public static byte[] longToBytes(long num) {
        byte[] bytes = new byte[8];
        for (int i = 0; i < 8; i++) {
            bytes[i] = (byte) (0xff & (num >> (i * 8)));
        }

        return bytes;
    }


    public static byte[] byteConvert32Bytes(BigInteger n) {
        byte tmpd[] = (byte[]) null;
        if (n == null) {
            return null;
        }

        if (n.toByteArray().length == 33) {
            tmpd = new byte[32];
            System.arraycopy(n.toByteArray(), 1, tmpd, 0, 32);
        } else if (n.toByteArray().length == 32) {
            tmpd = n.toByteArray();
        } else {
            tmpd = new byte[32];
            for (int i = 0; i < 32 - n.toByteArray().length; i++) {
                tmpd[i] = 0;
            }
            System.arraycopy(n.toByteArray(), 0, tmpd, 32 - n.toByteArray().length, n.toByteArray().length);
        }
        return tmpd;
    }


    public static BigInteger byteConvertInteger(byte[] b) {
        if (b[0] < 0) {
            byte[] temp = new byte[b.length + 1];
            temp[0] = 0;
            System.arraycopy(b, 0, temp, 1, b.length);
            return new BigInteger(temp);
        }
        return new BigInteger(b);
    }


    public static String getHexString(byte[] bytes) {
        return getHexString(bytes, true);
    }


    public static String getHexString(byte[] bytes, boolean upperCase) {
        return new String(encodeHex(bytes, !upperCase));
    }


    public static void printHexString(byte[] bytes) {
        for (int i = 0; i < bytes.length; i++) {
            String hex = Integer.toHexString(bytes[i] & 0xFF);
            if (hex.length() == 1) {
                hex = '0' + hex;
            }
            System.out.print("0x" + hex.toUpperCase() + ",");
        }
        System.out.println("");
    }


    public static byte[] hexStringToBytes(String hexString) {
        if (hexString == null || hexString.equals("")) {
            return null;
        }

        hexString = hexString.toUpperCase();
        int length = hexString.length() / 2;
        char[] hexChars = hexString.toCharArray();
        byte[] d = new byte[length];
        for (int i = 0; i < length; i++) {
            int pos = i * 2;
            d[i] = (byte) (charToByte(hexChars[pos]) << 4 | charToByte(hexChars[pos + 1]));
        }
        return d;
    }


    public static byte charToByte(char c) {
        return (byte) "0123456789ABCDEF".indexOf(c);
    }


    public static char[] encodeHex(byte[] data) {
        return encodeHex(data, true);
    }


    public static char[] encodeHex(byte[] data, boolean toLowerCase) {
        return encodeHex(data, toLowerCase ? DIGITS_LOWER : DIGITS_UPPER);
    }


    protected static char[] encodeHex(byte[] data, char[] toDigits) {
        int l = data.length;
        char[] out = new char[l << 1];
        for (int i = 0, j = 0; i < l; i++) {
            out[j++] = toDigits[(0xF0 & data[i]) >>> 4];
            out[j++] = toDigits[0x0F & data[i]];
        }
        return out;
    }


    public static String encodeHexString(byte[] data) {
        return encodeHexString(data, true);
    }


    public static String encodeHexString(byte[] data, boolean toLowerCase) {
        return encodeHexString(data, toLowerCase ? DIGITS_LOWER : DIGITS_UPPER);
    }


    protected static String encodeHexString(byte[] data, char[] toDigits) {
        return new String(encodeHex(data, toDigits));
    }


    public static byte[] decodeHex(char[] data) {
        int len = data.length;

        if ((len & 0x01) != 0) {
            throw new RuntimeException("Odd number of characters.");
        }

        byte[] out = new byte[len >> 1];

        for (int i = 0, j = 0; j < len; i++) {
            int f = toDigit(data[j], j) << 4;
            j++;
            f = f | toDigit(data[j], j);
            j++;
            out[i] = (byte) (f & 0xFF);
        }

        return out;
    }


    protected static int toDigit(char ch, int index) {
        int digit = Character.digit(ch, 16);
        if (digit == -1) {
            throw new RuntimeException("Illegal hexadecimal character " + ch
                    + " at index " + index);
        }
        return digit;
    }


    public static String StringToAsciiString(String content) {
        StringBuilder result = new StringBuilder(content.length() * 2);
        int max = content.length();
        for (int i = 0; i < max; i++) {
            result.append(Integer.toHexString(content.charAt(i)));
        }
        return result.toString();
    }


    public static String hexStringToString(String hexString, int encodeType) {
        int max = hexString.length() / encodeType;
        StringBuilder result = new StringBuilder(max);
        for (int i = 0; i < max; i++) {
            char c = (char) hexStringToAlgorism(hexString
                    .substring(i * encodeType, (i + 1) * encodeType));
            result.append(c);
        }
        return result.toString();
    }


    public static int hexStringToAlgorism(String hex) {
        hex = hex.toUpperCase();
        int max = hex.length();
        int result = 0;
        for (int i = max; i > 0; i--) {
            char c = hex.charAt(i - 1);
            int algorism = 0;
            if (c >= '0' && c <= '9') {
                algorism = c - '0';
            } else {
                algorism = c - 55;
            }
            result += Math.pow(16, max - i) * algorism;
        }
        return result;
    }


    public static String hexStringToBinary(String hex) {
        hex = hex.toUpperCase();
        StringBuilder result = new StringBuilder(hex.length() * 4);
        int max = hex.length();
        for (int i = 0; i < max; i++) {
            int index = "0123456789ABCDEF".indexOf(hex.charAt(i));
            if (index >= 0) {
                result.append(HEX_TO_BINARY[index]);
            }
        }
        return result.toString();
    }


    public static String AsciiStringToString(String content) {
        int length = content.length() / 2;
        StringBuilder result = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            String c = content.substring(i * 2, i * 2 + 2);
            result.append((char) hexStringToAlgorism(c));
        }
        return result.toString();
    }


    public static String algorismToHexString(int algorism, int maxLength) {
        String result = "";
        result = Integer.toHexString(algorism);

        if (result.length() % 2 == 1) {
            result = "0" + result;
        }
        return patchHexString(result.toUpperCase(), maxLength);
    }


    public static String byteToString(byte[] bytearray) {
        int length = bytearray.length;
        StringBuilder result = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            result.append((char) bytearray[i]);
        }
        return result.toString();
    }


    public static int binaryToAlgorism(String binary) {
        int max = binary.length();
        int result = 0;
        for (int i = max; i > 0; i--) {
            char c = binary.charAt(i - 1);
            int algorism = c - '0';
            result += Math.pow(2, max - i) * algorism;
        }
        return result;
    }


    public static String algorismToHEXString(int algorism) {
        String result = "";
        result = Integer.toHexString(algorism);

        if (result.length() % 2 == 1) {
            result = "0" + result;

        }
        result = result.toUpperCase();

        return result;
    }


    static public String patchHexString(String str, int maxLength) {
        StringBuilder temp = new StringBuilder(maxLength);
        for (int i = 0; i < maxLength - str.length(); i++) {
            temp.append('0');
        }
        return temp.append(str).substring(0, maxLength);
    }


    public static int parseToInt(String s, int defaultInt, int radix) {
        int i = 0;
        try {
            i = Integer.parseInt(s, radix);
        } catch (NumberFormatException ex) {
            i = defaultInt;
        }
        return i;
    }


    public static int parseToInt(String s, int defaultInt) {
        int i = 0;
        try {
            i = Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            i = defaultInt;
        }
        return i;
    }


    public static byte[] hexToByte(String hex)
            throws IllegalArgumentException {
        if (hex.length() % 2 != 0) {
            throw new IllegalArgumentException();
        }
        char[] arr = hex.toCharArray();
        byte[] b = new byte[hex.length() / 2];
        for (int i = 0, j = 0, l = hex.length(); i < l; i++, j++) {
            String swap = "" + arr[i++] + arr[i];
            int byteint = Integer.parseInt(swap, 16) & 0xFF;
            b[j] = (byte) byteint;
        }
        return b;
    }


    public static String byteToHex(byte b[]) {
        if (b == null) {
            throw new IllegalArgumentException(
                    "Argument b ( byte array ) is null! ");
        }
        StringBuilder hs = new StringBuilder(b.length * 2);
        for (int n = 0; n < b.length; n++) {
            String stmp = Integer.toHexString(b[n] & 0xff);
            if (stmp.length() == 1) {
                hs.append('0');
            }
            hs.append(stmp);
        }
        return hs.toString().toLowerCase();
    }

    public static byte[] subByte(byte[] input, int startIndex, int length) {
        byte[] bt = new byte[length];
        for (int i = 0; i < length; i++) {
            bt[i] = input[i + startIndex];
        }
        return bt;
    }
}
