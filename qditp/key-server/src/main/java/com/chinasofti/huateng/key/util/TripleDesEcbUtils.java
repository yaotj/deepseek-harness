package com.chinasofti.huateng.key.util;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESedeKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;


public final class TripleDesEcbUtils {
    private static final String NO_PADDING_TRANSFORMATION = "DESede/ECB/NoPadding";
    private static final String PKCS5_TRANSFORMATION = "DESede/ECB/PKCS5Padding";

    private TripleDesEcbUtils() {
    }


    public static byte[] decryptHex(String cipherHex, String keyHex) {
        return doFinal(hexToBytes(cipherHex), keyHex, Cipher.DECRYPT_MODE);
    }


    public static String encryptHex(String plainHex, String keyHex) {
        return bytesToHex(doFinal(hexToBytes(plainHex), keyHex, Cipher.ENCRYPT_MODE));
    }


    public static String encryptTextToBase64(String plainText, String keyText) {
        try {
            DESedeKeySpec keySpec = new DESedeKeySpec(keyText.getBytes(StandardCharsets.UTF_8));
            SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("DESede");
            SecretKey secretKey = keyFactory.generateSecret(keySpec);
            Cipher cipher = Cipher.getInstance(PKCS5_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new IllegalStateException("3DES字符串加密失败", e);
        }
    }

    private static byte[] doFinal(byte[] data, String keyHex, int mode) {
        try {
            Cipher cipher = Cipher.getInstance(NO_PADDING_TRANSFORMATION);
            cipher.init(mode, new SecretKeySpec(normalizeKey(hexToBytes(keyHex)), "DESede"));
            return cipher.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("3DES处理失败", e);
        }
    }

    private static byte[] normalizeKey(byte[] key) {
        if (key.length == 24) {
            return key;
        }
        if (key.length == 16) {
            byte[] key24 = new byte[24];
            System.arraycopy(key, 0, key24, 0, 16);
            System.arraycopy(key, 0, key24, 16, 8);
            return key24;
        }
        throw new IllegalArgumentException("3DES密钥长度必须为16字节或24字节");
    }

    private static byte[] hexToBytes(String hex) {
        if (hex == null || hex.length() % 2 != 0) {
            throw new IllegalArgumentException("HEX字符串长度必须为偶数");
        }
        byte[] result = new byte[hex.length() / 2];
        for (int i = 0; i < result.length; i++) {
            int index = i * 2;
            result[i] = (byte) Integer.parseInt(hex.substring(index, index + 2), 16);
        }
        return result;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02X", value & 0xFF));
        }
        return builder.toString();
    }
}
