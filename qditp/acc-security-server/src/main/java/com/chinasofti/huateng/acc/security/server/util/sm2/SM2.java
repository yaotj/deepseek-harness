package com.chinasofti.huateng.acc.security.server.util.sm2;

import com.chinasofti.huateng.acc.security.server.util.TransformUtils;
import org.apache.commons.codec.binary.Hex;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.generators.ECKeyPairGenerator;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECKeyGenerationParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.math.ec.ECCurve;
import org.bouncycastle.math.ec.ECFieldElement;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.math.ec.FixedPointCombMultiplier;
import org.bouncycastle.util.BigIntegers;
import org.bouncycastle.util.encoders.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

public class SM2 {
    private static final Logger log = LoggerFactory.getLogger(SM2.class);

    /**
     * y^3 = x^3+ax+b;
     * (gx,gy)为基点；
     */
    public static String[] sm2_param = {
            "FFFFFFFEFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF00000000FFFFFFFFFFFFFFFF",// p,0
            "FFFFFFFEFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF00000000FFFFFFFFFFFFFFFC",// a,1
            "28E9FA9E9D9F5E344D5A9E4BCF6509A7F39789F515AB8F92DDBCBD414D940E93",// b,2
            "FFFFFFFEFFFFFFFFFFFFFFFFFFFFFFFF7203DF6B21C6052B53BBF40939D54123",// n,3
            "32C4AE2C1F1981195F9904466A39C9948FE30BBFF2660BE1715A4589334C74C7",// gx,4
            "BC3736A2F4F6779C59BDCEE36B692153D0A9877CC62A474002DF32E52139F0A0" // gy,5
    };
    public static BigInteger ecc_a;
    public static BigInteger ecc_b;
    public static BigInteger ecc_gx;
    public static BigInteger ecc_gy;
    private static String userID = "1234567812345678";
    private static byte[] userIDBytes = userID.getBytes();
    private static SM2 sm2 = null;
    public String[] ecc_param = sm2_param;
    public BigInteger ecc_p;
    public BigInteger ecc_n;
    public ECCurve ecc_curve;
    public ECPoint ecc_point_g;

    public ECDomainParameters ecc_bc_spec;

    public ECKeyPairGenerator ecKeyPairGenerator;

    public SM2() {
        ECFieldElement ecc_gx_fieldelement;
        ECFieldElement ecc_gy_fieldelement;
        ecc_p = new BigInteger(ecc_param[0], 16);
        ecc_a = new BigInteger(ecc_param[1], 16);
        ecc_b = new BigInteger(ecc_param[2], 16);
        ecc_n = new BigInteger(ecc_param[3], 16);
        ecc_gx = new BigInteger(ecc_param[4], 16);
        ecc_gy = new BigInteger(ecc_param[5], 16);
        ecc_gx_fieldelement = new ECFieldElement.Fp(ecc_p, ecc_gx);
        ecc_gy_fieldelement = new ECFieldElement.Fp(ecc_p, ecc_gy);
        ecc_curve = new ECCurve.Fp(ecc_p, ecc_a, ecc_b);
        ecc_point_g = new ECPoint.Fp(ecc_curve, ecc_gx_fieldelement,
                ecc_gy_fieldelement);
        ecc_bc_spec = new ECDomainParameters(ecc_curve, ecc_point_g, ecc_n);
        ECKeyGenerationParameters ecc_ecgenparam;
        ecc_ecgenparam = new ECKeyGenerationParameters(ecc_bc_spec,
                new SecureRandom());
        ecKeyPairGenerator = new ECKeyPairGenerator();
        ecKeyPairGenerator.init(ecc_ecgenparam);
    }

    public static SM2 getInstance() {
        if (null == sm2) {
            sm2 = new SM2();
        }
        return sm2;
    }

    public static byte[] getZ(byte[] _userId, byte[] _x, byte[] _y) {
        SM3Digest sm3 = new SM3Digest();
        byte[] p;
        // userId length
        int len = _userId.length * 8;
        sm3.update((byte) (len >> 8 & 0x00ff));
        sm3.update((byte) (len & 0x00ff));
        // userId
        sm3.update(_userId, 0, _userId.length);
        // a,b
        p = BigIntegers.asUnsignedByteArray(ecc_a);
        sm3.update(p, 0, p.length);
        p = BigIntegers.asUnsignedByteArray(ecc_b);
        sm3.update(p, 0, p.length);
        // gx,gy
        p = BigIntegers.asUnsignedByteArray(ecc_gx);
        sm3.update(p, 0, p.length);
        p = BigIntegers.asUnsignedByteArray(ecc_gy);
        sm3.update(p, 0, p.length);
        // x,y
        p = _x;
        sm3.update(p, 0, p.length);
        p = _y;
        sm3.update(p, 0, p.length);
        // Z
        byte[] z = new byte[sm3.getDigestSize()];
        sm3.doFinal(z, 0);
        return z;
    }

    public static byte[] getZ(byte[] _userId) {
        SM3Digest sm3 = new SM3Digest();
        byte[] p;
        // userId length
        int len = _userId.length * 8;
        sm3.update((byte) (len >> 8 & 0x00ff));
        sm3.update((byte) (len & 0x00ff));
        // userId
        sm3.update(_userId, 0, _userId.length);
        // a,b
        p = BigIntegers.asUnsignedByteArray(ecc_a);
        sm3.update(p, 0, p.length);
        p = BigIntegers.asUnsignedByteArray(ecc_b);
        sm3.update(p, 0, p.length);
        // gx,gy
        p = BigIntegers.asUnsignedByteArray(ecc_gx);
        sm3.update(p, 0, p.length);
        p = BigIntegers.asUnsignedByteArray(ecc_gy);
        sm3.update(p, 0, p.length);

        // Z
        byte[] z = new byte[sm3.getDigestSize()];
        sm3.doFinal(z, 0);
        return z;
    }


    //测试SM2密钥对生成、签名、验签
    public static void Test1() {
        //生成SM2密钥对，前32字节是私钥，后64字节是公钥。
        byte[] byteArraySm2KeyPair = sm2.genSM2KeyPair();

        //截取私钥
        byte[] byteArraySm2PrivateKey = new byte[32];
        System.arraycopy(byteArraySm2KeyPair, 0, byteArraySm2PrivateKey, 0, 32);

        java.util.Base64.Encoder be = java.util.Base64.getEncoder();
        String base64Str = be.encodeToString(byteArraySm2PrivateKey);
        System.out.println("私钥=========>" + base64Str);
        //截取公钥
        byte[] byteArraySm2PublicKey = new byte[64];
        System.arraycopy(byteArraySm2KeyPair, 32, byteArraySm2PublicKey, 0, 64);

        String base64Str2 = be.encodeToString(byteArraySm2PublicKey);
        System.out.println("公钥=========>" + base64Str2);
        System.out.println("公钥HexString=========>" + TransformUtils.bytesToHex(byteArraySm2PublicKey));
        //用户ID，使用固定值1234567812345678。
        String strUserID = "1234567812345678";
        byte[] byteArrayUserID = strUserID.getBytes();

        //原文
        byte[] byteArrayOriginData = "abcTest1234".getBytes();
        String base64Str3 = be.encodeToString(byteArrayOriginData);
        System.out.println("原文=========>" + base64Str3);
        //计算哈希值
        byte[] byteArrayPublicKeyX = new byte[32];
        byte[] byteArrayPublicKeyY = new byte[32];
        System.arraycopy(byteArraySm2PublicKey, 0, byteArrayPublicKeyX, 0, 32);
        System.arraycopy(byteArraySm2PublicKey, 32, byteArrayPublicKeyY, 0, 32);
        byte[] byteArrayHash = sm2
                .getHash(byteArrayOriginData, byteArrayUserID, byteArrayPublicKeyX, byteArrayPublicKeyY);

        //签名
        byte[] byteArraySignData = sm2.sm2Sign(byteArrayHash, byteArraySm2PrivateKey);
        String base64Str4 = be.encodeToString(byteArraySignData);
        System.out.println("签名=========>" + base64Str4);
        System.out.println("签名HexString=========>" + TransformUtils.bytesToHex(byteArraySignData));
        //验签
        byte[] byteArraySignDataR = new byte[32];
        byte[] byteArraySignDataS = new byte[32];
        System.arraycopy(byteArraySignData, 0, byteArraySignDataR, 0, 32);
        System.arraycopy(byteArraySignData, 32, byteArraySignDataS, 0, 32);
        boolean bSm2VerifySign = sm2
                .sm2Verify(byteArrayHash, byteArrayPublicKeyX, byteArrayPublicKeyY, byteArraySignDataR, byteArraySignDataS);

        System.out.println("SM2密钥对生成、签名、验签：" + bSm2VerifySign + "\n");
    }

    //测试用博思给的原文、SM2签名值、SM2公钥做验签
    public static void Test2() {
        //博思给的Base64编码的原文、Base64编码的SM2签名值、Base64编码的SM2公钥
//        String strPublicKey = "fK4YR2f7r2OgnnE60plcFEC2vlLOSK41jPEzKkKysUFZRpQ7WdjZ9fpnl5x3lJfc7Vd9+swwPHuJLWq1yldLWA==";
//        String strSignData = "4UF1lpSLKHXRxCihfmpWQKy2PgnDX2QvcoJ2//qRahFwjoWg+I8ne39g/hKyaR9kIStVjcSnlPiSJMS/Pe8qVg==";
//        String strOriginData = "AiIyELeQAPjVs5+2nMUiw3+jN95CUB6ftulNum9rGKJQPtGg13c1/rbh9Utwe88MJJ4523IVqESAdDH/CjwDvPsAAQFiIS8AAQ==";
        String strPublicKey = "A8F743BF71C8491FE48763CACAEB7977AEC4E9E877A01275CDE7B37BE7EA33F493F57F100F328C15F9E9812AA6E34D378055890E5656EFA29451DC0C3E10CCBB";
        String strSignData = "11ADB642E08E022944AB587187AB8E55DC6090E2D307B4845B7FAD6B090F6479DF4928D5D37E177014F475A2DAE7768B9BE9FDE9BE0BF748419F68CDF3386926";
        String strOriginData = "23010100000112A000000111551000000404002103A8F743BF71C8491FE48763CACAEB7977AEC4E9E877A01275CDE7B37BE7EA33F48BC5E3B170ADE5BFD3570D46D20ED35EEDFF152087B3CB4EDFBE0593FDB9E4891494D3FCA50651ADA6C2630C341B2ED2201A9167B2E95348AC506749F785D2673530313536343630303737353638303000202012141707140000A410320200100001010001F4021DF9B6C6B287283E1C85077039D235D33FB8BD7ED2476D648E50B5311B77089160125439003C200143672101263200022200000000000000000000000000000000000000000000";

        //公钥Base64解码
//        byte[] byteArrayPublicKey = Base64.decode(strPublicKey);

        //签名值Base64解码
//        byte[] byteArraySignData = Base64.decode(strSignData);

        //原文Base64解码
//        byte[] byteArrayOriginData = Base64.decode(strOriginData);

        byte[] byteArrayPublicKey = TransformUtils.HexStringToByteArr(strPublicKey);
        byte[] byteArraySignData = TransformUtils.HexStringToByteArr(strSignData);
        byte[] byteArrayOriginData = TransformUtils.HexStringToByteArr(strOriginData);
        //用户ID，使用固定值1234567812345678。
        String strUserID = "1234567812345678";
        byte[] byteArrayUserID = strUserID.getBytes();

        //计算哈希值
        byte[] byteArrayPublicKeyX = new byte[32];
        byte[] byteArrayPublicKeyY = new byte[32];
        System.arraycopy(byteArrayPublicKey, 0, byteArrayPublicKeyX, 0, 32);
        System.arraycopy(byteArrayPublicKey, 32, byteArrayPublicKeyY, 0, 32);
        byte[] byteArrayHash = sm2
                .getHash(byteArrayOriginData, byteArrayUserID, byteArrayPublicKeyX, byteArrayPublicKeyY);

        //验签
        byte[] byteArraySignDataR = new byte[32];
        byte[] byteArraySignDataS = new byte[32];
        System.arraycopy(byteArraySignData, 0, byteArraySignDataR, 0, 32);
        System.arraycopy(byteArraySignData, 32, byteArraySignDataS, 0, 32);
        boolean bSm2VerifySign = sm2
                .sm2Verify(byteArrayHash, byteArrayPublicKeyX, byteArrayPublicKeyY, byteArraySignDataR, byteArraySignDataS);

        System.out.println("用博思给的原文、SM2签名值、SM2公钥做验签：" + bSm2VerifySign + "\n");
    }

    //  //测试用博思给的原文、SM2签名值、SM2公钥做验签
    public static void Test3() {
        //博思给的Base64编码的原文、Base64编码的SM2签名值、Base64编码的SM2公钥
        String strPublicKey = "U2lnbkNlcnRDYWxsQmFja0JvIHNpZ25DZXJ0Q2FsbEJhY2tCbyA9IG5ldyBTaWduQ2VydENhbGxCYWNrQm9hYQ==";
        String strSignData = "pN8JVTWP9Crv+jj4wykgoa0YCaTHzM9OvrFhswRfiwGGI5LTbhaq+yjM1yl4q6ZgobRS6rygqqLTmspsoZQ35g";
        String strOriginData = "MDAwMQ==MDI=U2lnbkNlcnRDYWxsQmFja0JvIHNpZ25DZXJ0Q2FsbEJhY2tCbyA9IG5ldyBTaWduQ2VydENhbGxCYWNrQm9hYQ==MjAyNzA2MjMyMzM2";
//    String strPublicKey = "wGq75nQW6aElr6FHOQ4fPc4gHuXK6IgHYr8XeaLUvwylvR77ioO8L/ORgwoyIIzDbA6rRhfBz4jArGuYl9zZwg==";
//    String strSignData = "NhbQnbv3K0a4ei0PLOPUKYKw/LeTmRv35vMun4Z23jWvbUiQskTtclmGY5vCiR4rd4UHVQYBZHFKGlxELfHthA==";
//    String strOriginData = "YWJjVGVzdDEyMzQ=";

        //公钥Base64解码
        byte[] byteArrayPublicKey = Base64.decode(strPublicKey);

        //签名值Base64解码
        byte[] byteArraySignData = Base64.decode(strSignData);

        //原文Base64解码
        byte[] byteArrayOriginData = Base64.decode(strOriginData);

        //用户ID，使用固定值1234567812345678。
        String strUserID = "1234567812345678";
        byte[] byteArrayUserID = strUserID.getBytes();

        //计算哈希值
        byte[] byteArrayPublicKeyX = new byte[32];
        byte[] byteArrayPublicKeyY = new byte[32];
        System.arraycopy(byteArrayPublicKey, 0, byteArrayPublicKeyX, 0, 32);
        System.arraycopy(byteArrayPublicKey, 32, byteArrayPublicKeyY, 0, 32);
        byte[] byteArrayHash = sm2
                .getHash(byteArrayOriginData, byteArrayUserID, byteArrayPublicKeyX, byteArrayPublicKeyY);

        //验签
        byte[] byteArraySignDataR = new byte[32];
        byte[] byteArraySignDataS = new byte[32];
        System.arraycopy(byteArraySignData, 0, byteArraySignDataR, 0, 32);
        System.arraycopy(byteArraySignData, 32, byteArraySignDataS, 0, 32);
        boolean bSm2VerifySign = sm2
                .sm2Verify(byteArrayHash, byteArrayPublicKeyX, byteArrayPublicKeyY, byteArraySignDataR, byteArraySignDataS);

        System.out.println("用博思给的原文、SM2签名值、SM2公钥做验签：" + bSm2VerifySign + "\n");
    }

    public static void main(String[] args) {
        // TODO Auto-generated method stub
        SM2 sm2 = SM2.getInstance();

        //测试SM2密钥对生成、签名、验签
//        Test1();

        //测试用博思给的原文、SM2签名值、SM2公钥做验签
//        Test2();
//        Test3();

        System.out.println(sm2.genKeyPairHex());
    }

    private byte[] getH(byte[] _Z, byte[] _plain) {
        byte[] bTmp = new byte[_Z.length + _plain.length];
        System.arraycopy(_Z, 0, bTmp, 0, _Z.length);
        System.arraycopy(_plain, 0, bTmp, _Z.length, _plain.length);
        return getHash(bTmp);
    }

    public byte[] getHash(byte[] _plain) {
        SM3Digest digest = new SM3Digest();
        digest.update(_plain, 0, _plain.length);
        byte out[] = new byte[32];
        digest.doFinal(out, 0);
        return out;
    }

    public byte[] getHash(byte[] _plain, byte[] _userId, byte[] _x, byte[] _y) {
        byte[] z = getZ(_userId, _x, _y);
        return getH(z, _plain);
    }
//
//    public byte[] getHash(byte[] _plain, byte[] _userId) {
//        byte[] z = getZ(_userId);
//        return getH(z, _plain);
//    }

    /**
     * 从私钥推导出公钥来
     *
     * @param priv_key
     * @return
     */
    public byte[] generatePublicKeyFromPrivKey(byte[] priv_key) {

        ECKeyGenerationParameters ecc_ecgenparam = new ECKeyGenerationParameters(ecc_bc_spec, new SecureRandom());
        ECKeyPairGenerator generator = new ECKeyPairGenerator();
        generator.init(ecc_ecgenparam);
        BigInteger d = BigIntegers.fromUnsignedByteArray(priv_key);
        ECKeyGenerationParameters ecP = (ECKeyGenerationParameters) ecc_ecgenparam;
        ECDomainParameters params = ecP.getDomainParameters();
        ECPoint Q = new FixedPointCombMultiplier().multiply(params.getG(), d);
        ECPublicKeyParameters pubKey = new ECPublicKeyParameters(Q, params);
        BigInteger biX = pubKey.getQ().getX().toBigInteger();
        BigInteger biY = pubKey.getQ().getY().toBigInteger();
        byte[] x = BigIntegers.asUnsignedByteArray(32, biX);
        byte[] y = BigIntegers.asUnsignedByteArray(32, biY);
        byte[] public_key_bytes = new byte[64];
        System.arraycopy(x, 0, public_key_bytes, 0, 32);
        System.arraycopy(y, 0, public_key_bytes, 32, 32);
        return public_key_bytes;

    }

    /**
     * 非压缩公钥转成压缩公钥
     *
     * @param publicKey
     * @return
     */
    public byte[] getCompressedPublicKeyFromNotCompressed(byte[] publicKey) {
        byte[] tmp = new byte[64 + 1];
        tmp[0] = 0x04; //0x04未压缩
        System.arraycopy(publicKey, 0, tmp, 1, 64);
        ECPoint tmpPoint = ecc_curve.decodePoint(tmp);
        byte[] compressed = tmpPoint.getEncoded(true);
        return compressed;
    }


    public byte[] genSM2KeyPair() {

        for (int i = 0; i < 20; i++) {
            AsymmetricCipherKeyPair keyPair = ecKeyPairGenerator.generateKeyPair();
            ECPrivateKeyParameters priKey = (ECPrivateKeyParameters) keyPair.getPrivate();
            ECPublicKeyParameters pubKey = (ECPublicKeyParameters) keyPair.getPublic();
            BigInteger biD = priKey.getD();
            BigInteger biX = pubKey.getQ().getX().toBigInteger();
            BigInteger biY = pubKey.getQ().getY().toBigInteger();
            byte[] d = BigIntegers.asUnsignedByteArray(32, biD);  // 私钥
            byte[] x = BigIntegers.asUnsignedByteArray(32, biX);  //公钥x
            byte[] y = BigIntegers.asUnsignedByteArray(32, biY);  // 公钥y

            if (x[0] != 0x00 && y[0] != 0x00) {   //某版本ios的sm2算法库有问题，服务端过滤掉这种他们不能处理的密钥
                byte[] byteArraySm2KeyPair = new byte[d.length + x.length + y.length];
                System.arraycopy(d, 0, byteArraySm2KeyPair, 0, d.length);
                System.arraycopy(x, 0, byteArraySm2KeyPair, d.length, x.length);
                System.arraycopy(y, 0, byteArraySm2KeyPair, d.length + x.length, y.length);
                return byteArraySm2KeyPair;
            }
            log.warn("生成的密钥IOS不能解析，继续生成新密钥,d={},x={},y={}", Hex.encodeHexString(d), Hex.encodeHexString(x),
                    Hex.encodeHexString(y));
        }
        //运行20次还不能生成一个有效的密钥对的话，就报错
        throw new RuntimeException("生成SM2密钥失败");

    }

    /**
     * @param _H hash
     */
    public byte[] sm2Sign(byte[] _H, byte[] _D) {
        BigInteger userD = new BigInteger(1, _D);
        BigInteger e = new BigInteger(1, _H);
        BigInteger k = null;
        ECPoint kp = null;
        BigInteger r = null;
        BigInteger s = null;
        do {
            do {
                AsymmetricCipherKeyPair keypair = ecKeyPairGenerator
                        .generateKeyPair();
                ECPrivateKeyParameters ecpriv = (ECPrivateKeyParameters) keypair
                        .getPrivate();
                ECPublicKeyParameters ecpub = (ECPublicKeyParameters) keypair
                        .getPublic();
                k = ecpriv.getD();
                kp = ecpub.getQ();
                r = e.add(kp.getX().toBigInteger());
                r = r.mod(ecc_n);
            } while (r.equals(BigInteger.ZERO) || r.add(k).equals(ecc_n));
            BigInteger da_1 = userD.add(BigInteger.ONE);
            da_1 = da_1.modInverse(ecc_n);
            s = r.multiply(userD);
            s = k.subtract(s).mod(ecc_n);
            s = da_1.multiply(s).mod(ecc_n);
        } while (s.equals(BigInteger.ZERO));
        byte[] arrayR = Utils.asUnsigned32ByteArray(r);
        byte[] arrayS = Utils.asUnsigned32ByteArray(s);
        byte[] bRet = new byte[arrayR.length + arrayS.length];
        System.arraycopy(arrayR, 0, bRet, 0, arrayR.length);
        System.arraycopy(arrayS, 0, bRet, arrayR.length, arrayS.length);
        return bRet;
    }

    public ECPoint getUserKey(byte[] _x, byte[] _y) {
        BigInteger x = new BigInteger(1, _x);
        BigInteger y = new BigInteger(1, _y);
        ECCurve sm2Curve = new ECCurve.Fp(ecc_p, ecc_a, ecc_b);
        return sm2Curve.createPoint(x, y, false);
    }

    public boolean sm2Verify(byte[] _h, byte[] _x, byte[] _y, byte[] _r,
                             byte[] _s) {
        ECPoint userKey = getUserKey(_x, _y);
        BigInteger r = new BigInteger(1, _r);
        BigInteger s = new BigInteger(1, _s);
        BigInteger e = new BigInteger(1, _h);
        BigInteger t = r.add(s).mod(ecc_n);
        if (t.equals(BigInteger.ZERO)) {
            return false;
        }
        ECPoint x1y1 = ecc_point_g.multiply(s);
        x1y1 = x1y1.add(userKey.multiply(t));
        BigInteger R = e.add(x1y1.getX().toBigInteger()).mod(ecc_n);
        return R.equals(r);
    }

    public ECPoint createPoint(BigInteger x, BigInteger y,
                               boolean withCompression) {
        return ecc_curve.createPoint(x, y, withCompression);
    }

    /**
     * 生成公私钥对
     */
    public Map genKeyPair() {
        //生成SM2密钥对，前32字节是私钥，后64字节是公钥。
        byte[] byteArraySm2KeyPair = sm2.genSM2KeyPair();

        //截取私钥
        byte[] byteArraySm2PrivateKey = new byte[32];
        System.arraycopy(byteArraySm2KeyPair, 0, byteArraySm2PrivateKey, 0, 32);

        java.util.Base64.Encoder be = java.util.Base64.getEncoder();
        String privateKey = be.encodeToString(byteArraySm2PrivateKey);
        //截取公钥
        byte[] byteArraySm2PublicKey = new byte[64];
        System.arraycopy(byteArraySm2KeyPair, 32, byteArraySm2PublicKey, 0, 64);

        String publicKey = be.encodeToString(byteArraySm2PublicKey);

        byte[] tmp = new byte[64 + 1];
        tmp[0] = 0x04; //0x04未压缩
        System.arraycopy(byteArraySm2PublicKey, 0, tmp, 1, 64);
        ECPoint tmpPoint = ecc_curve.decodePoint(tmp);
        byte[] compressed = tmpPoint.getEncoded(true);
        String compressed_base64 = be.encodeToString(compressed);

        Map map = new HashMap();
        map.put("privateKey", privateKey);
        map.put("publicKey", publicKey);
        map.put("publicKeyCompressed", compressed_base64);
        return map;
    }

    /**
     * 生成公私钥对
     */
    public Map genKeyPairHex() {
        //生成SM2密钥对，前32字节是私钥，后64字节是公钥。
        byte[] byteArraySm2KeyPair = sm2.genSM2KeyPair();

        //截取私钥
        byte[] byteArraySm2PrivateKey = new byte[32];
        System.arraycopy(byteArraySm2KeyPair, 0, byteArraySm2PrivateKey, 0, 32);

        String privateKey = TransformUtils.bytesToHex(byteArraySm2PrivateKey);
        //截取公钥
        byte[] byteArraySm2PublicKey = new byte[64];
        System.arraycopy(byteArraySm2KeyPair, 32, byteArraySm2PublicKey, 0, 64);

        String publicKey = TransformUtils.bytesToHex(byteArraySm2PublicKey);

        byte[] tmp = new byte[64 + 1];
        tmp[0] = 0x04; //0x04未压缩
        System.arraycopy(byteArraySm2PublicKey, 0, tmp, 1, 64);

        ECPoint tmpPoint = ecc_curve.decodePoint(tmp);
        byte[] compressed = tmpPoint.getEncoded(true);
        String compressed_str = TransformUtils.bytesToHex(compressed);
//        String compressed_str = TransformUtils.bytesToHex(compressed);

        Map map = new HashMap();
        map.put("privateKey", privateKey);
        map.put("publicKey", publicKey);
        map.put("publicKeyCompressed", compressed_str);
        return map;
    }

    /**
     * 签名
     *
     * @param contentHex
     * @param publicKeyHex  支持压缩公钥和不压缩公钥两种场景
     * @param privateKeyHex
     * @return
     */
    public String signHex(String contentHex, String publicKeyHex, String privateKeyHex) {

        if (publicKeyHex.length() != 64 * 2 && publicKeyHex.length() != 33 * 2) { //压缩33字节，未压缩64字节
            throw new RuntimeException("公钥长度不正确");
        }
        if (publicKeyHex.length() == 33 * 2 && !(publicKeyHex.substring(0, 2).equals("02") || publicKeyHex
                .substring(0, 2).equals("03"))) {
            throw new RuntimeException("压缩公钥要以02/03开始");
        }

        byte[] byteArrayPublicKeyX = new byte[32];
        byte[] byteArrayPublicKeyY = new byte[32];

        if (publicKeyHex.length() == 33 * 2) {
            //从压缩公钥里获得公钥
            ECPoint tmpPoint = ecc_curve.decodePoint(TransformUtils.HexStringToByteArr(publicKeyHex));
            ECFieldElement x = tmpPoint.getX();
            ECFieldElement y = tmpPoint.getY();
            byte[] _x = BigIntegers.asUnsignedByteArray(32, x.toBigInteger());
            byte[] _y = BigIntegers.asUnsignedByteArray(32, y.toBigInteger());
            System.arraycopy(_x, 0, byteArrayPublicKeyX, 0, 32);
            System.arraycopy(_y, 0, byteArrayPublicKeyY, 0, 32);

        } else { //未压缩的公钥
            byte[] byteArraySm2PublicKey = TransformUtils.HexStringToByteArr(publicKeyHex);
            System.arraycopy(byteArraySm2PublicKey, 0, byteArrayPublicKeyX, 0, 32);
            System.arraycopy(byteArraySm2PublicKey, 32, byteArrayPublicKeyY, 0, 32);
        }
        //私钥
        byte[] byteArraySm2PrivateKey = TransformUtils.HexStringToByteArr(privateKeyHex);
        //原文
        byte[] byteArrayOriginData = TransformUtils.HexStringToByteArr(contentHex);
        //计算哈希值
        byte[] byteArrayHash = sm2.getHash(byteArrayOriginData, userIDBytes, byteArrayPublicKeyX, byteArrayPublicKeyY);

        //签名
        byte[] byteArraySignData = sm2.sm2Sign(byteArrayHash, byteArraySm2PrivateKey);
        return TransformUtils.bytesToHex(byteArraySignData);
    }


    public String signHexWithUserId(String contentHex, String publicKeyHex, String privateKeyHex, byte[] userId) {

        if (publicKeyHex.length() != 64 * 2 && publicKeyHex.length() != 33 * 2) { //压缩33字节，未压缩64字节
            throw new RuntimeException("公钥长度不正确");
        }
        if (publicKeyHex.length() == 33 * 2 && !(publicKeyHex.substring(0, 2).equals("02") || publicKeyHex
                .substring(0, 2).equals("03"))) {
            throw new RuntimeException("压缩公钥要以02/03开始");
        }

        byte[] byteArrayPublicKeyX = new byte[32];
        byte[] byteArrayPublicKeyY = new byte[32];

        if (publicKeyHex.length() == 33 * 2) {
            //从压缩公钥里获得公钥
            ECPoint tmpPoint = ecc_curve.decodePoint(TransformUtils.HexStringToByteArr(publicKeyHex));
            ECFieldElement x = tmpPoint.getX();
            ECFieldElement y = tmpPoint.getY();
            byte[] _x = BigIntegers.asUnsignedByteArray(32, x.toBigInteger());
            byte[] _y = BigIntegers.asUnsignedByteArray(32, y.toBigInteger());
            System.arraycopy(_x, 0, byteArrayPublicKeyX, 0, 32);
            System.arraycopy(_y, 0, byteArrayPublicKeyY, 0, 32);

        } else { //未压缩的公钥
            byte[] byteArraySm2PublicKey = TransformUtils.HexStringToByteArr(publicKeyHex);
            System.arraycopy(byteArraySm2PublicKey, 0, byteArrayPublicKeyX, 0, 32);
            System.arraycopy(byteArraySm2PublicKey, 32, byteArrayPublicKeyY, 0, 32);
        }
        //私钥
        byte[] byteArraySm2PrivateKey = TransformUtils.HexStringToByteArr(privateKeyHex);
        //原文
        byte[] byteArrayOriginData = TransformUtils.HexStringToByteArr(contentHex);
        //计算哈希值 -- 用指定的userid来做
        byte[] byteArrayHash = sm2.getHash(byteArrayOriginData, userId, byteArrayPublicKeyX, byteArrayPublicKeyY);

        //签名
        byte[] byteArraySignData = sm2.sm2Sign(byteArrayHash, byteArraySm2PrivateKey);
        return TransformUtils.bytesToHex(byteArraySignData);
    }


    /**
     * 从私钥推导出公钥后，再计算签名
     *
     * @param contentHex
     * @param privateKeyHex
     * @return
     */
    public String signHex(String contentHex, String privateKeyHex) {
        //私钥
        byte[] byteArraySm2PrivateKey = TransformUtils.HexStringToByteArr(privateKeyHex);
        //公钥
        byte[] byteArraySm2PublicKey = generatePublicKeyFromPrivKey(byteArraySm2PrivateKey);
        byte[] byteArrayPublicKeyX = new byte[32];
        byte[] byteArrayPublicKeyY = new byte[32];
        System.arraycopy(byteArraySm2PublicKey, 0, byteArrayPublicKeyX, 0, 32);
        System.arraycopy(byteArraySm2PublicKey, 32, byteArrayPublicKeyY, 0, 32);
        //原文
        byte[] byteArrayOriginData = TransformUtils.HexStringToByteArr(contentHex);
        //计算哈希值
        byte[] byteArrayHash = sm2.getHash(byteArrayOriginData, userIDBytes, byteArrayPublicKeyX, byteArrayPublicKeyY);
        //签名
        byte[] byteArraySignData = sm2.sm2Sign(byteArrayHash, byteArraySm2PrivateKey);
        return TransformUtils.bytesToHex(byteArraySignData);
    }

    /**
     * 目前只有羊城通是没有hash的，这里先不支持压缩公钥
     *
     * @param contentHex
     * @param publicKeyHex
     * @param privateKeyHex
     * @return
     */
    public String signHexWithoutHash(String contentHex, String publicKeyHex, String privateKeyHex) {

        //私钥
        byte[] byteArraySm2PrivateKey = TransformUtils.HexStringToByteArr(privateKeyHex);
        //公钥
        byte[] byteArraySm2PublicKey = TransformUtils.HexStringToByteArr(publicKeyHex);
        //原文
        byte[] byteArrayOriginData = TransformUtils.HexStringToByteArr(contentHex);
        //计算哈希值
        byte[] byteArrayPublicKeyX = new byte[32];
        byte[] byteArrayPublicKeyY = new byte[32];
        System.arraycopy(byteArraySm2PublicKey, 0, byteArrayPublicKeyX, 0, 32);
        System.arraycopy(byteArraySm2PublicKey, 32, byteArrayPublicKeyY, 0, 32);

        //byte[] byteArrayHash = sm2.getHash(byteArrayOriginData, userIDBytes, byteArrayPublicKeyX, byteArrayPublicKeyY);

        //签名
        //byte[] byteArraySignData = sm2.sm2Sign(byteArrayHash, byteArraySm2PrivateKey);
        byte[] byteArraySignData = sm2.sm2Sign(byteArrayOriginData, byteArraySm2PrivateKey);
        return TransformUtils.bytesToHex(byteArraySignData);
    }

//    public String signBase64(String content, String publicKey, String privateKey) {
//
//        //私钥
//        byte[] byteArraySm2PrivateKey = Base64.decode(privateKey);
//        //公钥
//        byte[] byteArraySm2PublicKey = Base64.decode(publicKey);
//        //原文
//        byte[] byteArrayOriginData = content.getBytes();
//        //计算哈希值
//        byte[] byteArrayPublicKeyX = new byte[32];
//        byte[] byteArrayPublicKeyY = new byte[32];
//        System.arraycopy(byteArraySm2PublicKey, 0, byteArrayPublicKeyX, 0, 32);
//        System.arraycopy(byteArraySm2PublicKey, 32, byteArrayPublicKeyY, 0, 32);
//        byte[] byteArrayHash = sm2.getHash(byteArrayOriginData, userIDBytes, byteArrayPublicKeyX, byteArrayPublicKeyY);
//
//        //签名
//        byte[] byteArraySignData = sm2.sm2Sign(byteArrayHash, byteArraySm2PrivateKey);
//        return new String(Base64.encode(byteArraySignData));
//    }

//    public String signBase64(String content, String publicKey, String privateKey) {
//
//        //私钥
//        byte[] byteArraySm2PrivateKey = Base64.decode(privateKey);
//        //公钥
//        byte[] byteArraySm2PublicKey = Base64.decode(publicKey);
//        //原文
//        byte[] byteArrayOriginData = content.getBytes();
//        //计算哈希值
//        byte[] byteArrayPublicKeyX = new byte[32];
//        byte[] byteArrayPublicKeyY = new byte[32];
//        System.arraycopy(byteArraySm2PublicKey, 0, byteArrayPublicKeyX, 0, 32);
//        System.arraycopy(byteArraySm2PublicKey, 32, byteArrayPublicKeyY, 0, 32);
//        byte[] byteArrayHash = sm2.getHash(byteArrayOriginData, userIDBytes, byteArrayPublicKeyX, byteArrayPublicKeyY);
//
//        //签名
//        byte[] byteArraySignData = sm2.sm2Sign(byteArrayHash, byteArraySm2PrivateKey);
//        return new String(Base64.encode(byteArraySignData));
//    }

    public boolean verifyHex(String content, String publicKeyHex, String signData) {

        if (publicKeyHex.length() != 64 * 2 && publicKeyHex.length() != 33 * 2) { //压缩33字节，未压缩64字节
            throw new RuntimeException("公钥长度不正确");
        }
        if (publicKeyHex.length() == 33 * 2 && !(publicKeyHex.substring(0, 2).equals("02") || publicKeyHex
                .substring(0, 2).equals("03"))) {
            throw new RuntimeException("压缩公钥要以02/03开始");
        }

        byte[] byteArrayPublicKeyX = new byte[32];
        byte[] byteArrayPublicKeyY = new byte[32];
        if (publicKeyHex.length() == 33 * 2) {
            //从压缩公钥里获得公钥
            ECPoint tmpPoint = ecc_curve.decodePoint(TransformUtils.HexStringToByteArr(publicKeyHex));
            ECFieldElement x = tmpPoint.getX();
            ECFieldElement y = tmpPoint.getY();
            byte[] _x = BigIntegers.asUnsignedByteArray(32, x.toBigInteger());
            byte[] _y = BigIntegers.asUnsignedByteArray(32, y.toBigInteger());
            System.arraycopy(_x, 0, byteArrayPublicKeyX, 0, 32);
            System.arraycopy(_y, 0, byteArrayPublicKeyY, 0, 32);
        } else {
            //未压缩的公钥
            byte[] byteArraySm2PublicKey = TransformUtils.HexStringToByteArr(publicKeyHex);
            System.arraycopy(byteArraySm2PublicKey, 0, byteArrayPublicKeyX, 0, 32);
            System.arraycopy(byteArraySm2PublicKey, 32, byteArrayPublicKeyY, 0, 32);
        }

        //原文
        byte[] byteArrayOriginData = TransformUtils.HexStringToByteArr(content);
        //计算哈希值

        byte[] byteArrayHash = sm2.getHash(byteArrayOriginData, userIDBytes, byteArrayPublicKeyX, byteArrayPublicKeyY);

        byte[] byteArraySignData = TransformUtils.HexStringToByteArr(signData);
        //验签
        byte[] byteArraySignDataR = new byte[32];
        byte[] byteArraySignDataS = new byte[32];
        System.arraycopy(byteArraySignData, 0, byteArraySignDataR, 0, 32);
        System.arraycopy(byteArraySignData, 32, byteArraySignDataS, 0, 32);
        boolean bSm2VerifySign = sm2
                .sm2Verify(byteArrayHash, byteArrayPublicKeyX, byteArrayPublicKeyY, byteArraySignDataR, byteArraySignDataS);

        return bSm2VerifySign;
    }


    /**
     * 目前只有羊城通是没有hash的，这里先不支持压缩公钥
     *
     * @param content
     * @param publicKeyHex
     * @param signData
     * @return
     */
    public boolean verifyHexWithoutHash(String content, String publicKeyHex, String signData) {

        //公钥
        byte[] byteArraySm2PublicKey = TransformUtils.HexStringToByteArr(publicKeyHex);
        //原文
        byte[] byteArrayOriginData = TransformUtils.HexStringToByteArr(content);
        //计算哈希值
        byte[] byteArrayPublicKeyX = new byte[32];
        byte[] byteArrayPublicKeyY = new byte[32];
        System.arraycopy(byteArraySm2PublicKey, 0, byteArrayPublicKeyX, 0, 32);
        System.arraycopy(byteArraySm2PublicKey, 32, byteArrayPublicKeyY, 0, 32);

        //byte[] byteArrayHash = sm2.getHash(byteArrayOriginData, userIDBytes, byteArrayPublicKeyX, byteArrayPublicKeyY);

        byte[] byteArraySignData = TransformUtils.HexStringToByteArr(signData);
        //验签
        byte[] byteArraySignDataR = new byte[32];
        byte[] byteArraySignDataS = new byte[32];
        System.arraycopy(byteArraySignData, 0, byteArraySignDataR, 0, 32);
        System.arraycopy(byteArraySignData, 32, byteArraySignDataS, 0, 32);
        //boolean bSm2VerifySign = sm2.sm2Verify(byteArrayHash, byteArrayPublicKeyX, byteArrayPublicKeyY, byteArraySignDataR, byteArraySignDataS);
        boolean bSm2VerifySign = sm2
                .sm2Verify(byteArrayOriginData, byteArrayPublicKeyX, byteArrayPublicKeyY, byteArraySignDataR,
                        byteArraySignDataS);

        return bSm2VerifySign;
    }

//    public boolean verifyBase64(String content, String publicKey, String signData) {
//
//        //公钥
//        byte[] byteArraySm2PublicKey = Base64.decode(publicKey);
//        //原文
//        byte[] byteArrayOriginData = Base64.decode(content);
//        //计算哈希值
//        byte[] byteArrayPublicKeyX = new byte[32];
//        byte[] byteArrayPublicKeyY = new byte[32];
//        System.arraycopy(byteArraySm2PublicKey, 0, byteArrayPublicKeyX, 0, 32);
//        System.arraycopy(byteArraySm2PublicKey, 32, byteArrayPublicKeyY, 0, 32);
//        byte[] byteArrayHash = sm2.getHash(byteArrayOriginData, userIDBytes, byteArrayPublicKeyX, byteArrayPublicKeyY);
//
//        byte[] byteArraySignData = Base64.decode(signData);
//        //验签
//        byte[] byteArraySignDataR = new byte[32];
//        byte[] byteArraySignDataS = new byte[32];
//        System.arraycopy(byteArraySignData, 0, byteArraySignDataR, 0, 32);
//        System.arraycopy(byteArraySignData, 32, byteArraySignDataS, 0, 32);
//        boolean bSm2VerifySign = sm2.sm2Verify(byteArrayHash, byteArrayPublicKeyX, byteArrayPublicKeyY, byteArraySignDataR, byteArraySignDataS);
//
//        return bSm2VerifySign;
//    }

}
