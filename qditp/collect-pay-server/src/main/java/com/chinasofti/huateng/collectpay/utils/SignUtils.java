package com.chinasofti.huateng.collectpay.utils;

import com.chinasofti.huateng.collectpay.config.PayCenterProperties;
import com.chinasofti.huateng.collectpay.model.request.PayCenterRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Slf4j
public class SignUtils {

    @Autowired
    private  PayCenterProperties payCenterProperties;
    private  final String SIGN_ALGORITHM = "SHA256WithRSA";


    // 签名
    public  String signRequest(PayCenterRequest request) {
        String privateKey = payCenterProperties.getPrivateKey();
        log.info("privateKey is {}",privateKey);
        if (!StringUtils.isEmpty(privateKey)) {
            String signData = buildSignData(request);
            String sign = signWithRsa(signData, privateKey);
            log.info("---签名 sign is {}",sign);
            return sign;
        }else {
            log.info("私钥为空,结束");
        }
        return null;
    }

    private  String buildSignData(PayCenterRequest request) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("merchantNo", request.getMerchantNo());
        params.put("apiVersion", request.getApiVersion());
        params.put("signType", request.getSignType());
        params.put("charset", request.getCharset());
        params.put("bizData", request.getBizData());

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (sb.length() > 0) {
                sb.append("&");
            }
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return sb.toString();
    }

    private  String signWithRsa(String data, String privateKey) {
        try {
            if (StringUtils.isEmpty(privateKey)) {
                log.warn("商户私钥为空");
                return "";
            }
            byte[] keyBytes = Base64.getDecoder().decode(privateKey);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PrivateKey key = keyFactory.generatePrivate(keySpec);

            Signature signature = Signature.getInstance(SIGN_ALGORITHM);
            signature.initSign(key);
            signature.update(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception e) {
            log.error("RSA签名异常", e);
            return "";
        }
    }

}
