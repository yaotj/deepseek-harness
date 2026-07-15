package com.chinasofti.huateng.acc.security.server.itp.util;

public final class ItpPboc3DesMacUtils {
    public static final byte[] ZERO_IVC = new byte[]{0, 0, 0, 0, 0, 0, 0, 0};

    private ItpPboc3DesMacUtils() {
    }

    public static byte[] calculatePboc3desMAC(byte[] data, byte[] key, byte[] icv) throws Exception {
        if (key == null || data == null) {
            throw new IllegalArgumentException("data or key is null");
        }
        if (key.length != 16) {
            throw new IllegalArgumentException("key length is not 16 byte");
        }
        byte[] leftKey = new byte[8];
        System.arraycopy(key, 0, leftKey, 0, 8);

        int blockCount = data.length / 8 + 1;
        int lastBlockLength = data.length % 8;
        byte[][] blocks = new byte[blockCount][8];
        for (int i = 0; i < blockCount; i++) {
            int copyLength = i == blockCount - 1 ? lastBlockLength : 8;
            System.arraycopy(data, i * 8, blocks[i], 0, copyLength);
        }
        blocks[blockCount - 1][lastBlockLength] = (byte) 0x80;

        byte[] desXor = ItpDesUtils.xor(blocks[0], icv);
        for (int i = 1; i < blockCount; i++) {
            byte[] des = ItpDesUtils.encryptByDesCbc(desXor, leftKey);
            desXor = ItpDesUtils.xor(blocks[i], des);
        }
        return ItpDesUtils.encryptBy3DesCbc(desXor, key);
    }
}
