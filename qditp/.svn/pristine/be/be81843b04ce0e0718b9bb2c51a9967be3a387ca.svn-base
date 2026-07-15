package com.chinasofti.huateng.acc.security.server.util.sm2;


import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERBitString;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.asn1.x509.X509CertificateStructure;
import org.bouncycastle.math.ec.ECPoint;

import java.io.*;
import java.math.BigInteger;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;


public class SM2SMUtil {

    private static SM2 sm2 = SM2.getInstance();

    public static String parsing() {
        File file = new File("C:\\Users\\Administrator\\Desktop\\国密\\SM2.cer");
        ASN1Sequence seq = null;
        ASN1InputStream aIn;
        try {
            FileInputStream fis = new FileInputStream(file);
            byte[] buf = new byte[fis.available()];
            fis.read(buf);
            fis.close();
            String strBuf = new String(buf);
            // Base64解码、加码用于判断是否为base64格式
            String strCertSub = strBuf.substring(27, strBuf.length() - 27);
            byte[] byteCert = Base64.getMimeDecoder().decode(strCertSub);
            String strCert = Base64.getMimeEncoder().encodeToString(byteCert);
            if (strCertSub.replaceAll("\r\n", "").equalsIgnoreCase(strCert.replaceAll("\r\n", ""))) {
                buf = byteCert;
            }
            InputStream inStream = new ByteArrayInputStream(buf);
            aIn = new ASN1InputStream(inStream);
            seq = (ASN1Sequence) aIn.readObject();
            X509CertificateStructure cert = new X509CertificateStructure(seq);
            SubjectPublicKeyInfo subjectPublicKeyInfo = cert.getSubjectPublicKeyInfo();
            DERBitString publicKeyData = subjectPublicKeyInfo.getPublicKeyData();
            byte[] publicKey = publicKeyData.getEncoded();
            if (publicKey.length != 68) {
                throw new SM2Exception("非SM2证书");
            }
            byte[] encodedPublicKey = publicKey;
            byte[] eP = new byte[64];
            System.arraycopy(encodedPublicKey, 4, eP, 0, eP.length);
            return Utils.toHexStringNoBlank(eP);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 生成公私钥对
     */
    public static Map genKeyPair() {
        String keypair = Utils.toHexStringNoBlank(sm2.genSM2KeyPair());
        String d = keypair.substring(0, 64);
        String x = keypair.substring(64, 128);
        String y = keypair.substring(128, 192);
        Map map = new HashMap();
        map.put("privateKey", d);
        map.put("publicKey", x + y);
        return map;
    }

    /**
     * 根据用户ID签名 Str
     *
     * @param userid  用户di
     * @param pubkey  公钥
     * @param prvKey  byte  私钥
     * @param content 明文
     */
    public static String sign(String userid, String pubkey, byte[] prvKey, String content)
            throws SM2Exception {

        if (userid == null) {
            throw new SM2Exception("用户身份标示不能为空");
        }
        if (content == null) {
            throw new SM2Exception("待签名消息不能为空");
        }
        int index = pubkey.length();
        byte[] x = Utils.bytesFromHexStringNoBlank(pubkey.substring(0, index / 2));
        byte[] y = Utils.bytesFromHexStringNoBlank(pubkey.substring(index / 2, index));
        return Utils.toHexStringNoBlank(sign(userid.getBytes(), x, y, prvKey, content.getBytes()));
    }

    /**
     * 根据用户ID签名 都为byte
     *
     * @param userid  用户id
     * @param x       公钥x
     * @param y       公钥y
     * @param prvKey  私钥
     * @param content 明文
     */
    public static byte[] sign(byte[] userid, byte[] x, byte[] y, byte[] prvKey, byte[] content) {

        SM2 sm2 = SM2.getInstance();
        byte[] hash = sm2.getHash(content, userid, x, y);
        return sm2.sm2Sign(hash, prvKey);
    }

    /**
     * SM2私钥签名，带用户ID。
     */
    public static byte[] testSM2SignWithID_byte(byte[] ID, byte[] priKeybytes, byte[] sourcebytes,
                                                byte[] x, byte[] y) {
        byte[] userZa = SM2.getZ(ID, x, y);
        SM3Digest sm3 = new SM3Digest();
        sm3.update(userZa, 0, userZa.length);
        sm3.update(sourcebytes, 0, sourcebytes.length);
        byte[] dg = new byte[32];
        sm3.doFinal(dg, 0);
        SM2 sm2 = SM2.getInstance();
        byte[] signature = sm2.sm2Sign(dg, priKeybytes);
        return signature;
    }

    /**
     * 验签Str
     */
    public static boolean verify(String userid, String pubkey, String sign, String content)
            throws SM2Exception {

        if (userid == null) {
            throw new SM2Exception("用户身份标示不能为空");
        }
        if (content == null) {
            throw new SM2Exception("待签名消息不能为空");
        }
        if (sign == null) {
            throw new SM2Exception("签名值不能为空");
        }
        int index = pubkey.length();
        byte[] x = Utils.bytesFromHexStringNoBlank(pubkey.substring(0, index / 2));
        byte[] y = Utils.bytesFromHexStringNoBlank(pubkey.substring(index / 2, index));
        return verify(userid.getBytes(), x, y, sign, content.getBytes());
    }

    /**
     * 验签Byte
     */
    public static boolean verify(byte[] userid, byte[] x, byte[] y, String sign, byte[] content)
            throws SM2Exception {

        SM2 sm2 = SM2.getInstance();
        byte[] hash = sm2.getHash(content, userid, x, y);
        byte[] byteSign = Utils.bytesFromHexStringNoBlank(sign);
        ;
        int len = byteSign.length / 2;
        byte[] _r = new byte[len];
        System.arraycopy(byteSign, 0, _r, 0, len);
        byte[] _s = new byte[len];
        System.arraycopy(byteSign, len, _s, 0, len);
        return sm2.sm2Verify(hash, x, y, _r, _s);
    }

    /**
     * 加密Str
     */
    public static String encryptBySM2(String pubKey, String plaintext) throws SM2Exception {
        if (plaintext == null) {
            throw new SM2Exception("待加密信息不能为空");
        }
        int index = pubKey.length();
        byte[] x = Utils.bytesFromHexStringNoBlank(pubKey.substring(0, index / 2));
        byte[] y = Utils.bytesFromHexStringNoBlank(pubKey.substring(index / 2, index));
        byte[] ciphertext = encryptBySM2(x, y, plaintext.getBytes());
        return Base64.getEncoder().encodeToString(ciphertext);
    }

    /**
     * 加密byte
     */
    public static byte[] encryptBySM2(byte[] x, byte[] y, byte[] data) {
        SM2 sm2 = SM2.getInstance();
        Cipher cipher = new Cipher();
        ECPoint userKey = sm2.getUserKey(x, y);
        ECPoint c1 = cipher.init_enc(sm2, userKey);
        cipher.encrypt(data);
        byte[] c3 = new byte[32];
        cipher.doFinal(c3);
        int datalen = data.length;
        byte[] enCode = c1.getEncoded();
        byte[] ciphertext = new byte[datalen + 97];
        System.arraycopy(enCode, 0, ciphertext, 0, 65);
        System.arraycopy(data, 0, ciphertext, 65, data.length);
        System.arraycopy(c3, 0, ciphertext, 65 + datalen, c3.length);
        return ciphertext;
    }

    /**
     * 解密
     */
    public static String decryptBySm2(byte[] prikey, String ciphertext) throws SM2Exception {
        if (ciphertext == null) {
            throw new SM2Exception("加密信息不能为空");
        }
        BigInteger priKey = new BigInteger(1, prikey);
        byte[] cipherCode = Base64.getDecoder().decode(ciphertext);
        int cipherlen = cipherCode.length - 97;
        byte[] purecipher = new byte[cipherlen];
        System.arraycopy(cipherCode, 65, purecipher, 0, cipherlen);
        Cipher cipher = new Cipher();
        byte[] c1 = new byte[65];
        System.arraycopy(cipherCode, 0, c1, 0, 65);
        ECPoint point = SM2.getInstance().ecc_curve.decodePoint(c1);
        cipher.init_dec(priKey, point);
        cipher.decrypt(purecipher);
        byte[] c3 = new byte[32];
        System.arraycopy(cipherCode, 65 + cipherlen, c3, 0, 32);
        byte[] c3_ = new byte[32];
        cipher.doFinal(c3_);
        String C3_ = Utils.toHexStringNoBlank(c3_);
        String C3 = Utils.toHexStringNoBlank(c3);
        if (!C3_.equals(C3)) {
            throw new SM2Exception("未通过C3校验，解密失败");
        }
        return new String(purecipher);
    }
}
