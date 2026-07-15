package com.chinasofti.huateng.acc.security.server.util.sm2;

import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.util.BigIntegers;

import java.math.BigInteger;


public class Cipher {
    private int ct = 1;

    private ECPoint p2;
    private SM3Digest sm3keybase;
    private SM3Digest sm3c3;

    private byte[] key = new byte[32];
    private byte keyOff = 0;

    public Cipher() {
    }

    public static void main(String[] args) {
        byte[] x = Utils
                .bytesFromHexStringNoBlank("a0403da8144dde72c642ec5ffd9949c3aedb0af49d2501e7e15732910e58a788");
        byte[] y = Utils
                .bytesFromHexStringNoBlank("a266c08e7d9a5a282896d73cbcfe3715629cd36341383391f6dcdc79d96813a2");
        byte[] d = Utils.bytesFromHexStringNoBlank("964761caa8d7e370d4bc4a7cdf74fe2e4d17e78981e412a889dea2e2c14c182d");
        BigInteger priKey = new BigInteger(1, d);
        byte[] data = "abcd".getBytes();
        SM2 sm2 = SM2.getInstance();
        Cipher cipher = new Cipher();
        ECPoint userKey = sm2.getUserKey(x, y);
        ECPoint c1 = cipher.init_enc(sm2, userKey);
        cipher.encrypt(data);
        byte[] c3 = new byte[32];
        System.out.println("data1==" + Utils.toHexStringNoBlank(data));
        cipher.doFinal(c3);
        System.out.println("data2==" + Utils.toHexStringNoBlank(data));
        System.out.println("c1==\n" + Utils.toHexStringNoBlank(c1.getEncoded()));
        System.out.println("c3==\n" + Utils.toHexStringNoBlank(c3));


        cipher.init_dec(priKey, c1);

        System.out.println("data3==" + Utils.toHexStringNoBlank(data));
        cipher.decrypt(data);
        System.out.println("data4==" + Utils.toHexStringNoBlank(data));
        byte[] c3_ = new byte[32];
        cipher.doFinal(c3_);
        System.out.println("c3_==" + Utils.toHexStringNoBlank(c3_));


    }

    private void reset() {
        sm3keybase = new SM3Digest();
        sm3c3 = new SM3Digest();

        byte[] p;

//		p = p2.getX().toBigInteger().toByteArray();
        p = BigIntegers.asUnsignedByteArray(p2.getX().toBigInteger());
        sm3keybase.update(p, 0, p.length);
        sm3c3.update(p, 0, p.length);

//		p = p2.getY().toBigInteger().toByteArray();
        p = BigIntegers.asUnsignedByteArray(p2.getY().toBigInteger());
        sm3keybase.update(p, 0, p.length);

        ct = 1;
        NextKey();
    }

    private void NextKey() {
        SM3Digest sm3keycur = new SM3Digest(sm3keybase);
        sm3keycur.update((byte) (ct >> 24 & 0x00ff));
        sm3keycur.update((byte) (ct >> 16 & 0x00ff));
        sm3keycur.update((byte) (ct >> 8 & 0x00ff));
        sm3keycur.update((byte) (ct & 0x00ff));
        sm3keycur.doFinal(key, 0);
        keyOff = 0;
        ct++;
    }

    public ECPoint init_enc(SM2 sm2, ECPoint userKey) {
        BigInteger k = null;
        ECPoint c1 = null;
        AsymmetricCipherKeyPair key = sm2.ecKeyPairGenerator.generateKeyPair();
        ECPrivateKeyParameters ecpriv = (ECPrivateKeyParameters) key
                .getPrivate();
        ECPublicKeyParameters ecpub = (ECPublicKeyParameters) key.getPublic();

        k = ecpriv.getD();
        c1 = ecpub.getQ();

        p2 = userKey.multiply(k);
        reset();

        return c1;
    }

    public void encrypt(byte[] data) {
        sm3c3.update(data, 0, data.length);
        for (int i = 0; i < data.length; i++) {
            if (keyOff == key.length)
                NextKey();
            data[i] ^= key[keyOff++];
        }
    }

    public void init_dec(BigInteger userD, ECPoint c1) {
        p2 = c1.multiply(userD);
        reset();
    }

    public void decrypt(byte[] data) {
        for (int i = 0; i < data.length; i++) {
            if (keyOff == key.length)
                NextKey();
            data[i] ^= key[keyOff++];
        }
        sm3c3.update(data, 0, data.length);
    }

    public void doFinal(byte[] c3) {
        System.out.println("HASH.y=" + p2.getY().toBigInteger());
        byte[] p = p2.getY().toBigInteger().toByteArray();
        sm3c3.update(p, 0, p.length);
        sm3c3.doFinal(c3, 0);
        reset();
    }
}
