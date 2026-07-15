package com.chinasofti.huateng.paysign.util;


import com.alibaba.fastjson2.JSON;
import org.apache.commons.codec.binary.Base64;

import java.nio.charset.Charset;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.TreeMap;


public class RSASignUtils {

    private final static String CHARACTER_ENCODING_UTF_8 = "UTF-8";


    /**
     * RSA私钥加签
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
     * 公钥验签
     *
     * @param
     * @param
     * @param
     * @return 验签结果 true验证一致 false验证不一致
     */
    public static boolean verify(String pubKeyOri, String plainText, String signText) {
        try {
            byte[] pubKeyText = pubKeyOri.getBytes(CHARACTER_ENCODING_UTF_8);
            // 解密由base64编码的公钥,并构造X509EncodedKeySpec对象
            java.security.spec.X509EncodedKeySpec bobPubKeySpec = new java.security.spec.X509EncodedKeySpec(
                    Base64.decodeBase64(pubKeyText));
            // RSA算法
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            // 取公钥匙对象
            java.security.PublicKey pubKey = keyFactory.generatePublic(bobPubKeySpec);
            Signature signature = Signature.getInstance("SHA256WithRSA");
            signature.initVerify(pubKey);
            signature.update(plainText.getBytes("UTF-8"));
            return signature.verify(Base64.decodeBase64(signText.getBytes("UTF-8")));
        } catch (Throwable e) {
            return false;
        }
    }

//    public static void main(String[] args) throws Exception {
//
//        String priKey = "MIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQCX+asjrvFrtDlDhHxmrZyDHTXwbCb/p5zoxnuv4FW2/Z5sUjk2o4lGmcwG43pTwbjMamjvAczheZGWPKNlgASCfWp5WFZrpYevdG6nO/ykyTUwq3Gw3clcC2bbNyU57eW2mfuXbrNLY1F7O+gLhbRN0uDOJ9+oXpJSQq2oUYR7pQLJvhAgh9khkPaYX0LNU16gXSuyvJZcVh5e/fG3Hzjqid6pGpxgAK8z9bqUoPQJEsMQsoN5EBriDu8k4CevY7FkNmTkWKkVQjcbm3vyIw+3S8JmmHV8OQgr6JwthpYCTpMGU55Nhqw9Sc8dAbdYEsqvZiCuz4W4faVPaiKv+QZ5AgMBAAECggEAISt9DuzABKHxXass+7wozPUzMKZqXKJYvPWVSW3NR4NTcJEBq8tFywMGZPqBWIaPrw4KmR/rd8rw27mgcCbg1RpKgvGk6xnV45WmERomqlDAKz9AMTo3GL/kUzRWC67A1HoHT6X5vBqRTjRlER69m77LEjPhvP3nMc0b2gDwyYuBwjEXSKSp/8fWd/mA97uD/CNfBCc/AJm2c0Kma9svdl5b269NMcUkjyuWEYLbpwxcTDXIYGhX1XJTqwWnKjAiLOjdJ2+eA39fCvj3Zz0aRUhft/96H0nNPnZCLu9MW8g5BTvzxH4122aJY5/Y/ANlQ3D8aQfRHcJufmyWTIMOAQKBgQD7xuN+k3jrL1N5p0pDO0EFiXDDuhIKnKt2sIMHWuDs4aOgRsbtcrDEuYk9el2j9pyjwrkdQIDkqjL9FXwEjxvHaXVa8eqP7g+3syr95WxCXdeiAgEQIm2FQvYL5Rdzfj33ifw0hqa46yDhPFAO2SPC3CADmvZ6CpimsLkarE/EZQKBgQCahj0w4CxgaHimwLBUBdJIDsYfGlgFBarQry0j8bDsFBst4dc/Ot9EB5frr921Q5IgAqBC43/jNxrHui1nkUKJ0VJTiNLMEXsvndgcHPSgorZ4lUku9zgZ9OljhNQD9s+E6MfJ+9CWqCFl9t0+nn9UchaU4V+8i8FEeY25rU0mhQKBgE7OTOyUejea45TjYOI0TMDP6STKO9Vofl6zFwAZWOesJIwJO0CdMmkQ7bz5bQ/iI9s6GrjmNLHd+AGxVNUUegxrnNjveYy9ZdwIz38S7VTEjLbfy7diH0ej0uGcPj/fFsRBQ1ipgMvGhM8bEq/jFUdroPWf7l/6qxcZn4aSwpDlAoGAX+DbJvBxmIA6HH2C6x+RklRYagQWiUcy7blD8QGOHW18T8PJotoMnlF32i2NC2OZz3LRra8rMviGDVdfxNtExe6zflFvOl++Z5Uw+oCc6O8M+VTny9RpYvGrvqw0QSFrMbSeAp3UlyZLUtESBkCiEOuZR1dv57VvfeVOIt9892ECgYBj46ko/R2oy49/V/gggDv+A5kf1jRkF+eVVJirbp70Wc2ycqfcRmwaOEjMYNf/GF4+e+rSF0uO8SVWFC6MWj8epmPzQ+lMf0gazF1DdxWN0/a1zh8GRB6wDGYOrl8kRvh/SmMUfz15fTt3asH9EyHpuOU/vtauqmEEryZY5/2HQA==";
//
//
//        String json = contractDismissal();
//
//        TreeMap treeMap = JSON.parseObject(json, TreeMap.class);
//        String sortStr = SortUtil.joinStr(treeMap);
//
//        ApiRequest apiRequest = new ApiRequest()
//                .setBizData(cn.hutool.core.codec.Base64.encode(json, Charset.forName("UTF-8")))
//                .setApiVersion("1.0.0")
//                .setMerchantNo("ABCDEFGH")
//                .setSignType("RSA2")
//                .setCharset("UTF-8")
//                .setSign(sign(priKey, sortStr));
//
//        System.out.println(JSON.toJSONString(apiRequest));
//
//    }
//
//    public static String contract(){
//        ContractRequest contractRequest = new ContractRequest();
//        contractRequest.setPaymentVendor("0801");
//        contractRequest.setDisplayAccount("城交大数据");
//        contractRequest.setThirdUserId("00142910");
//        contractRequest.setRequestSignSeq("0014291012345678");
//        contractRequest.setMobilePhone("13800000000");
//        contractRequest.setCertNo("110101199001011234");
//        contractRequest.setCustName("张三");
//        contractRequest.setNotifyUrl("notify2");
//        return JSON.toJSONString(contractRequest);
//    }
//
//    public static String contractDismissal(){
//        ContractDismissalRequest contractDismissalRequest = new ContractDismissalRequest();
//        contractDismissalRequest.setRequestSignSeq("0014291012345678");
//        return JSON.toJSONString(contractDismissalRequest);
//    }

}