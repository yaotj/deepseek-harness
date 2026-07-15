package com.chinasofti.huateng.acc.security.server.util.sm2;

import com.chinasofti.huateng.acc.security.server.util.TransformUtils;

/**
 * @author houkepan
 * @date 2021/3/22 19:08
 */
public class Main {
    public static void main(String[] args) {
        getSign("1111");
    }

    private static void test1() {
        SM2 sm2 = new SM2();

        //生成SM2密钥对，前32字节是私钥，后64字节是公钥。
//        byte[] byteArraySm2KeyPair = sm2.genSM2KeyPair();

        String privatekey = "224525589868531772E03470B5187F242C4D54994A52EEA2E9F6ACC0DD0043D9";
        String publickey = "797BBE15308FC3C6A2AE8EC65337CDD0665DBDFEE3BE0671F42045F93B91C6DB05653512F22817B1E9277375AB0A2D65A752475DC1963E6F71D85E56E3C58161";
        String str = "6d65737361676520646967657374";
        //截取私钥
        byte[] byteArraySm2PrivateKey = TransformUtils.HexStringToByteArr(privatekey);
        //截取公钥
        byte[] byteArraySm2PublicKey = TransformUtils.HexStringToByteArr(publickey);

        //用户ID，使用固定值1234567812345678。
        String strUserID = "1234567812345678";
        byte[] byteArrayUserID = strUserID.getBytes();

        //原文
        byte[] byteArrayOriginData = TransformUtils.HexStringToByteArr(str);
        //计算哈希值
        byte[] byteArrayPublicKeyX = new byte[32];
        byte[] byteArrayPublicKeyY = new byte[32];
        System.arraycopy(byteArraySm2PublicKey, 0, byteArrayPublicKeyX, 0, 32);
        System.arraycopy(byteArraySm2PublicKey, 32, byteArrayPublicKeyY, 0, 32);
        byte[] byteArrayHash = sm2
                .getHash(byteArrayOriginData, byteArrayUserID, byteArrayPublicKeyX, byteArrayPublicKeyY);

        //签名
        byte[] byteArraySignData = sm2.sm2Sign(byteArrayHash, byteArraySm2PrivateKey);
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

    private static void test2() {
        SM2 sm2 = new SM2();
        String strPublicKey = "09F9DF311E5421A150DD7D161E4BC5C672179FAD1833FC076BB08FF356F35020CCEA490CE26775A52DC6EA718CC1AA600AED05FBF35E084A6632F6072DA9AD13";
        String strSignData = "0C46D8F6574657223E89CB4EB6CD557DB11D509EC0448C5885B67170E9ED2DDC828AF587D4A35C7CDD47470C6A4F3213E009ED2EC66B03B827EB4E110BE141BF";
        String strOriginData = "6d65737361676520646967657374";

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

    private static String getSign(String str) {
        SM2 sm2 = new SM2();

        String privatekey = "224525589868531772E03470B5187F242C4D54994A52EEA2E9F6ACC0DD0043D9";
        String publickey = "797BBE15308FC3C6A2AE8EC65337CDD0665DBDFEE3BE0671F42045F93B91C6DB05653512F22817B1E9277375AB0A2D65A752475DC1963E6F71D85E56E3C58161";
        //截取私钥
        byte[] byteArraySm2PrivateKey = TransformUtils.HexStringToByteArr(privatekey);
        //截取公钥
        byte[] byteArraySm2PublicKey = TransformUtils.HexStringToByteArr(publickey);

        //用户ID，使用固定值1234567812345678。
        String strUserID = "1234567812345678";
        byte[] byteArrayUserID = strUserID.getBytes();

        //原文
        byte[] byteArrayOriginData = TransformUtils.HexStringToByteArr(str);
        //计算哈希值
        byte[] byteArrayPublicKeyX = new byte[32];
        byte[] byteArrayPublicKeyY = new byte[32];
        System.arraycopy(byteArraySm2PublicKey, 0, byteArrayPublicKeyX, 0, 32);
        System.arraycopy(byteArraySm2PublicKey, 32, byteArrayPublicKeyY, 0, 32);
        byte[] byteArrayHash = sm2
                .getHash(byteArrayOriginData, byteArrayUserID, byteArrayPublicKeyX, byteArrayPublicKeyY);

        //签名
        byte[] byteArraySignData = sm2.sm2Sign(byteArrayHash, byteArraySm2PrivateKey);
//        log.info("签名HexString=========>" + TransformUtils.bytesToHex(byteArraySignData));

        return TransformUtils.bytesToHex(byteArraySignData);
    }


}
