package com.chinasofti.huateng.acc.security.server.util.sm2;

import org.bouncycastle.crypto.Digest;

/**
 * @author GuoYouCheng
 */
public abstract class GeneralDigest implements Digest {
    private static int BYTE_LENGTH = 64;

    private byte[] xBuf;
    private int xBufOff;

    private long byteCount;

    public GeneralDigest() {
        xBuf = new byte[4];
    }

    public GeneralDigest(GeneralDigest t) {
        xBuf = new byte[t.xBuf.length];
        System.arraycopy(t.xBuf, 0, xBuf, 0, xBuf.length);
        xBufOff = t.xBufOff;
        byteCount = t.byteCount;
    }

    public void update(byte input) {
        xBuf[xBufOff++] = input;
        if (xBufOff == xBuf.length) {
            ProcessWord(xBuf, 0);
            xBufOff = 0;
        }
        byteCount++;
    }

    public void update(byte[] input, int inOff, int length) {
        //
        // fill the current word
        //
        while ((xBufOff != 0) && (length > 0)) {
            update(input[inOff]);
            inOff++;
            length--;
        }

        //
        // process whole words.
        //
        while (length > xBuf.length) {
            ProcessWord(input, inOff);
            inOff += xBuf.length;
            length -= xBuf.length;
            byteCount += xBuf.length;
        }

        //
        // load in the remainder.
        //
        while (length > 0) {
            update(input[inOff]);
            inOff++;
            length--;
        }
    }

    public void Finish() {
        long bitLength = (byteCount << 3);

        //
        // add the pad bytes.
        //
        update((byte) 128);

        while (xBufOff != 0)
            update((byte) 0);
        ProcessLength(bitLength);
        ProcessBlock();
    }

    public void reset() {
        byteCount = 0;
        xBufOff = 0;
        for (int i = 0; i < xBuf.length; i++) {
            xBuf[i] = 0;
        }
    }

    public int GetByteLength() {
        return BYTE_LENGTH;
    }

    protected abstract void ProcessWord(byte[] input, int inOff);

    protected abstract void ProcessLength(long bitLength);

    protected abstract void ProcessBlock();

    public abstract String getAlgorithmName();

    public abstract int getDigestSize();

    public abstract int doFinal(byte[] output, int outOff);
}
