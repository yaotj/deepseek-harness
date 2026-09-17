package com.chinasofti.huateng.paysign.util;

import com.alibaba.fastjson2.JSON;
import org.apache.commons.codec.binary.Base64;

import java.nio.charset.Charset;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RSASignUtils {

    private final static String CHARACTER_ENCODING_UTF_8 = "UTF-8";
    private static final Logger log = LoggerFactory.getLogger(RSASignUtils.class);

    /**
     * RSA私钥加签。
     *
     * @param
     * @param
     * @return 十六进制的签名字符串
     * @throws Exception
     */
    public static String sign(String priKeyOri, String plainText) throws Exception {
        byte[] priKeyText = priKeyOri.getBytes(CHARACTER_ENCODING_UTF_8);
        PKCS8EncodedKeySpec priPKCS8 = new PKCS8EncodedKeySpec(Base64.decodeBase64(priKeyText));
        KeyFactory keyf = KeyFactory.getInstance("RSA");
        PrivateKey prikey = keyf.generatePrivate(priPKCS8);
        try {
            Signature signature = Signature.getInstance("SHA256WithRSA");
            signature.initSign(prikey);
            signature.update(plainText.getBytes("UTF-8"));
            return Base64.encodeBase64String(signature.sign());
        } catch (Exception e) {
            throw e;
        }
    }

    /**
     * 公钥验签。
     *
     * @param
     * @param
     * @param
     * @return 验签结果 true验证一致 false验证不一致
     */
    public static boolean verify(String pubKeyOri, String plainText, String signText) {
        try {
            byte[] pubKeyText = pubKeyOri.getBytes(CHARACTER_ENCODING_UTF_8);
            java.security.spec.X509EncodedKeySpec bobPubKeySpec = new java.security.spec.X509EncodedKeySpec(
                    Base64.decodeBase64(pubKeyText));
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            java.security.PublicKey pubKey = keyFactory.generatePublic(bobPubKeySpec);
            Signature signature = Signature.getInstance("SHA256WithRSA");
            signature.initVerify(pubKey);
            signature.update(plainText.getBytes("UTF-8"));
            return signature.verify(Base64.decodeBase64(signText.getBytes("UTF-8")));
        } catch (java.security.SignatureException e) {
            log.warn("RSA验签失败", e);
            return false;
        } catch (Exception e) {
            log.error("RSA验签系统异常", e);
            throw new IllegalStateException("RSA验签系统异常", e);
        }
    }

}