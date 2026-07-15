package com.chinasofti.huateng.key.util;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESedeKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 3DES ECB模式工具类。
 *
 * <p>IF8A-02 中用户私钥需要先用 ACC 侧 3DES 密钥解密，再用 APP_SERVER 侧
 * 3DES 密钥加密后返回给 APP。旧系统配置可能使用16字节双长度密钥或24字节三长度密钥；
 * 本工具在遇到16字节密钥时按 K1 + K2 + K1 扩展为24字节。</p>
 */
public final class TripleDesEcbUtils {
    private static final String NO_PADDING_TRANSFORMATION = "DESede/ECB/NoPadding";
    private static final String PKCS5_TRANSFORMATION = "DESede/ECB/PKCS5Padding";

    private TripleDesEcbUtils() {
    }

    /**
     * 使用3DES ECB模式解密HEX密文。
     *
     * @param cipherHex HEX格式密文
     * @param keyHex HEX格式3DES密钥，支持16字节或24字节
     * @return 解密后的明文字节
     */
    public static byte[] decryptHex(String cipherHex, String keyHex) {
        return doFinal(hexToBytes(cipherHex), keyHex, Cipher.DECRYPT_MODE);
    }

    /**
     * 使用3DES ECB模式加密HEX明文。
     *
     * @param plainHex HEX格式明文
     * @param keyHex HEX格式3DES密钥，支持16字节或24字节
     * @return HEX格式密文
     */
    public static String encryptHex(String plainHex, String keyHex) {
        return bytesToHex(doFinal(hexToBytes(plainHex), keyHex, Cipher.ENCRYPT_MODE));
    }

    /**
     * 按旧系统 ThreeDES.encryptThreeDESECB 的方式加密字符串。
     *
     * <p>旧工具类使用 DESede/ECB/PKCS5Padding，明文和密钥都按 UTF-8 字符串取字节，
     * 加密结果用 Base64 输出。IF8A-02 返回给 APP 的 keyPrivate 需要保持这种格式。</p>
     *
     * @param plainText 明文字符串
     * @param keyText 3DES密钥字符串
     * @return Base64格式密文
     */
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
