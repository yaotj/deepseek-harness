package com.chinasofti.huateng.acc.security.server.itp.util;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESKeySpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

public final class ItpDesUtils {
    public static final byte[] ZERO_IVC = new byte[]{0, 0, 0, 0, 0, 0, 0, 0};

    private ItpDesUtils() {
    }

    public static byte[] encryptBy3DesCbc(byte[] content, byte[] key) throws GeneralSecurityException {
        byte[] key24 = new byte[24];
        System.arraycopy(key, 0, key24, 0, 16);
        System.arraycopy(key, 0, key24, 16, 8);
        Cipher cipher = Cipher.getInstance("DESede/CBC/NoPadding");
        SecretKey secureKey = new SecretKeySpec(key24, "DESede");
        cipher.init(Cipher.ENCRYPT_MODE, secureKey, new IvParameterSpec(ZERO_IVC));
        return cipher.doFinal(content);
    }

    public static byte[] encryptByDesCbc(byte[] content, byte[] key) throws GeneralSecurityException {
        SecureRandom secureRandom = new SecureRandom();
        SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("DES");
        SecretKey secretKey = keyFactory.generateSecret(new DESKeySpec(key));
        Cipher cipher = Cipher.getInstance("DES/CBC/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, new IvParameterSpec(ZERO_IVC), secureRandom);
        return cipher.doFinal(content);
    }

    public static byte[] xor(byte[] left, byte[] right) {
        byte[] result = new byte[Math.min(left.length, right.length)];
        for (int i = 0; i < result.length; i++) {
            result[i] = (byte) (left[i] ^ right[i]);
        }
        return result;
    }
}
