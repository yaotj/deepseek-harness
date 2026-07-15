package com.chinasofti.huateng.acc.security.server.service;

import com.chinasofti.huateng.acc.security.server.util.TransformUtils;
import com.chinasofti.huateng.acc.security.server.util.sm2.SM2;

import java.util.Base64;

public class Main {

    /**
     * 生成公钥证书->设备
     *
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        String publickey = "09186767688CE9303325AE74C4B3B3359FF1FCE0A18B69B00197B270BA24D6E06FCA4491822F2F449781B15B1F0F5DE5B783686D8C8D560297CAB4648CAD6DDF";
        byte[] bytes = TransformUtils.HexStringToByteArr(publickey);
        String s = Base64.getEncoder().encodeToString(bytes);
        // 第10字段公钥
        System.out.println("s = " + s);

        //证书1-10字段
        //120100000001205000000104000088CRhnZ2iM6TAzJa50xLOzNZ/x/OChi2mwAZeycLok1uBvykSRgi8vRJeBsVsfD13lt4NobYyNVgKXyrRkjK1t3w==

        //证书1、3-10字段
        String temp = "120100000001015000000104000088CRhnZ2iM6TAzJa50xLOzNZ/x/OChi2mwAZeycLok1uBvykSRgi8vRJeBsVsfD13lt4NobYyNVgKXyrRkjK1t3w==atsLXLojaXwG61SkUL+AI9jBRqexaNKHUy4GBgC/r6S48Ifz9tawoOPhzYcIEeyqFFotb8Avlnqwnnk0+RZtvQ==";

        String sign = getCertificateSign(temp);
        System.out.println("sign = " + sign);

        //D46E9FC1CD61400312F7B87E68EC64257AEDD3DA4D91A481C5A1823850BCA8139B581EEB8C83C7750735B3C8E4DFD29277E99E7E0A1590B6042DB1BC3118356F

        //公钥证书-测试
        //120100000001205000000104000088CRhnZ2iM6TAzJa50xLOzNZ/x/OChi2mwAZeycLok1uBvykSRgi8vRJeBsVsfD13lt4NobYyNVgKXyrRkjK1t3w==D46E9FC1CD61400312F7B87E68EC64257AEDD3DA4D91A481C5A1823850BCA8139B581EEB8C83C7750735B3C8E4DFD29277E99E7E0A1590B6042DB1BC3118356F
    }

    public static String getCertificateSign(String str) {
        SM2 sm2 = new SM2();
        String publickey = "09186767688CE9303325AE74C4B3B3359FF1FCE0A18B69B00197B270BA24D6E06FCA4491822F2F449781B15B1F0F5DE5B783686D8C8D560297CAB4648CAD6DDF";
        String privatekey = "23BC2047612E360B03A15ABD0B1F695C4AE2E3B3902BE19E4956F015F5E157B4";

        //截取私钥
        byte[] byteArraySm2PrivateKey = TransformUtils.HexStringToByteArr(privatekey);
        //截取公钥
        byte[] byteArraySm2PublicKey = TransformUtils.HexStringToByteArr(publickey);

        //用户ID，使用固定值1234567812345678。
        String strUserID = "1234567812345678";
        byte[] byteArrayUserID = strUserID.getBytes();

        //原文
//        byte[] byteArrayOriginData = TransformUtils.HexStringToByteArr(str);
        byte[] byteArrayOriginData = str.getBytes();
        //计算哈希值
        byte[] byteArrayPublicKeyX = new byte[32];
        byte[] byteArrayPublicKeyY = new byte[32];
        System.arraycopy(byteArraySm2PublicKey, 0, byteArrayPublicKeyX, 0, 32);
        System.arraycopy(byteArraySm2PublicKey, 32, byteArrayPublicKeyY, 0, 32);
        byte[] byteArrayHash = sm2
                .getHash(byteArrayOriginData, byteArrayUserID, byteArrayPublicKeyX, byteArrayPublicKeyY);

        //签名
        byte[] byteArraySignData = sm2.sm2Sign(byteArrayHash, byteArraySm2PrivateKey);
        System.out.println("签名数据：" + str + " \n签名HexString=========>" + TransformUtils.bytesToHex(byteArraySignData));

        return Base64.getEncoder().encodeToString(byteArraySignData);
    }

}
