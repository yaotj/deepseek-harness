package com.chinasofti.huateng.acc.security.server.util.sm2;


/**
 * @author GuoYouCheng
 */
public class SM3Digest extends GeneralDigest {

    private final static int[] v0 = new int[]{(int) 0x7380166f,
            (int) 0x4914b2b9, (int) 0x172442d7, (int) 0xda8a0600,
            (int) 0xa96f30bc, (int) 0x163138aa, (int) 0xe38dee4d,
            (int) 0xb0fb0e4e};
    private static int[] X0 = new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0};
    private final int DIGEST_LENGTH = 32;
    private int[] v = new int[8];
    private int[] v_ = new int[8];
    private int[] X = new int[68];
    private int xOff;
    private int T_00_15 = 0x79cc4519;
    private int T_16_63 = 0x7a879d8a;

    public SM3Digest() {
        reset();
    }

    public SM3Digest(SM3Digest t) {
        super(t);
        System.arraycopy(t.X, 0, X, 0, t.X.length);
        xOff = t.xOff;
        System.arraycopy(t.v, 0, v, 0, t.v.length);
    }

    public static void IntToBigEndian(int n, byte[] bs, int off) {
        bs[off] = (byte) (SupportClass.URShift(n, 24));
        bs[++off] = (byte) (SupportClass.URShift(n, 16));
        bs[++off] = (byte) (SupportClass.URShift(n, 8));
        bs[++off] = (byte) (n);
    }

    public void reset() {
        super.reset();
        System.arraycopy(v0, 0, v, 0, v0.length);
        xOff = 0;
        System.arraycopy(X0, 0, X, 0, X0.length);
    }

    @Override
    protected void ProcessBlock() {
        int i;

        int[] ww = X;
        int[] ww_ = new int[64];

        for (i = 16; i < 68; i++) {
            ww[i] = P1(ww[i - 16] ^ ww[i - 9] ^ (ROTATE(ww[i - 3], 15)))
                    ^ (ROTATE(ww[i - 13], 7)) ^ ww[i - 6];
        }

        for (i = 0; i < 64; i++) {
            ww_[i] = ww[i] ^ ww[i + 4];
        }

        int[] vv = v;
        int[] vv_ = v_;

        System.arraycopy(vv, 0, vv_, 0, vv.length);

        int SS1, SS2, TT1, TT2, aaa;
        for (i = 0; i < 16; i++) {
            aaa = ROTATE(vv_[0], 12);
            SS1 = aaa + vv_[4] + ROTATE(T_00_15, i);
            SS1 = ROTATE(SS1, 7);
            SS2 = SS1 ^ aaa;

            TT1 = FF_00_15(vv_[0], vv_[1], vv_[2]) + vv_[3] + SS2 + ww_[i];
            TT2 = GG_00_15(vv_[4], vv_[5], vv_[6]) + vv_[7] + SS1 + ww[i];
            vv_[3] = vv_[2];
            vv_[2] = ROTATE(vv_[1], 9);
            vv_[1] = vv_[0];
            vv_[0] = TT1;
            vv_[7] = vv_[6];
            vv_[6] = ROTATE(vv_[5], 19);
            vv_[5] = vv_[4];
            vv_[4] = P0(TT2);
        }
        for (i = 16; i < 64; i++) {
            aaa = ROTATE(vv_[0], 12);
            SS1 = aaa + vv_[4] + ROTATE(T_16_63, i);
            SS1 = ROTATE(SS1, 7);
            SS2 = SS1 ^ aaa;

            TT1 = FF_16_63(vv_[0], vv_[1], vv_[2]) + vv_[3] + SS2 + ww_[i];
            TT2 = GG_16_63(vv_[4], vv_[5], vv_[6]) + vv_[7] + SS1 + ww[i];
            vv_[3] = vv_[2];
            vv_[2] = ROTATE(vv_[1], 9);
            vv_[1] = vv_[0];
            vv_[0] = TT1;
            vv_[7] = vv_[6];
            vv_[6] = ROTATE(vv_[5], 19);
            vv_[5] = vv_[4];
            vv_[4] = P0(TT2);
        }
        for (i = 0; i < 8; i++) {
            vv[i] ^= vv_[i];
        }

        // Reset
        xOff = 0;
        System.arraycopy(X0, 0, X, 0, X0.length);

    }

    @Override
    protected void ProcessWord(byte[] in_Renamed, int inOff) {
        int n = in_Renamed[inOff] << 24;
        n |= (in_Renamed[++inOff] & 0xff) << 16;
        n |= (in_Renamed[++inOff] & 0xff) << 8;
        n |= (in_Renamed[++inOff] & 0xff);
        X[xOff] = n;
        if (++xOff == 16) {
            ProcessBlock();
        }
    }

    @Override
    protected void ProcessLength(long bitLength) {
        if (xOff > 14) {
            ProcessBlock();
        }
        X[14] = (int) (SupportClass.URShift(bitLength, 32));
        X[15] = (int) (bitLength & (int) 0xffffffff);
    }

    @Override
    public String getAlgorithmName() {
        return "SM3";
    }

    @Override
    public int getDigestSize() {
        return DIGEST_LENGTH;
    }

    @Override
    public int doFinal(byte[] out_Renamed, int outOff) {
        Finish();
        for (int i = 0; i < 8; i++) {
            IntToBigEndian(v[i], out_Renamed, outOff + i * 4);
        }
        reset();
        return DIGEST_LENGTH;
    }

    // ////////////////////////////////////////////
    private int ROTATE(int x, int n) {
        return (x << n) | (SupportClass.URShift(x, (32 - n)));
    }

    private int P0(int X) {
        return ((X) ^ ROTATE((X), 9) ^ ROTATE((X), 17));
    }

    private int P1(int X) {
        return ((X) ^ ROTATE((X), 15) ^ ROTATE((X), 23));
    }

    private int FF_00_15(int X, int Y, int Z) {
        return (X ^ Y ^ Z);
    }

    private int FF_16_63(int X, int Y, int Z) {
        return ((X & Y) | (X & Z) | (Y & Z));
    }

    private int GG_00_15(int X, int Y, int Z) {
        return (X ^ Y ^ Z);
    }

    private int GG_16_63(int X, int Y, int Z) {
        return ((X & Y) | (~X & Z));
    }
}
