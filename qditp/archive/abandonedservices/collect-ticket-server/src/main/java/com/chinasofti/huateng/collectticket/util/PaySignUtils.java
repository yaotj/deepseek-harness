package com.chinasofti.huateng.collectticket.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

/**
 * Pay center signature utility.
 */
@Component
public class PaySignUtils {
    private static final Logger log = LoggerFactory.getLogger(PaySignUtils.class);

    @Value("${pay.center.paycenter-public-key:}")
    private String paycenterPublicKey;

    @Value("${pay.center.merchant-private-key:}")
    private String merchantPrivateKey;

    private static final String SIGN_ALGORITHM = "SHA256WithRSA";

    /**
     * Sign request parameters.
     *
     * @param params parameters to sign
     * @return signature string
     */
    public String sign(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }
        try {
            String signData = buildSignData(params);
            return signWithRsa(signData, merchantPrivateKey);
        } catch (Exception e) {
            log.error("Sign failed", e);
            return "";
        }
    }

    /**
     * Verify pay center callback notification.
     *
     * @param params parameters to verify
     * @param sign   signature
     * @return whether verification passed
     */
    public boolean verify(Map<String, String> params, String sign) {
        if (params == null || params.isEmpty() || !StringUtils.hasText(sign)) {
            return false;
        }
        if (!StringUtils.hasText(paycenterPublicKey)) {
            log.warn("Pay center public key not configured, skip verification");
            return true;
        }
        try {
            String signData = buildSignData(params);
            return verifyWithRsa(signData, sign, paycenterPublicKey);
        } catch (Exception e) {
            log.error("Verify failed", e);
            return false;
        }
    }

    /**
     * Build signature data.
     * Sort by parameter name alphabetically, concatenate as key=value&key=value.
     */
    private String buildSignData(Map<String, String> params) {
        TreeMap<String, String> sortedParams = new TreeMap<>(params);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : sortedParams.entrySet()) {
            if (sb.length() > 0) {
                sb.append("&");
            }
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return sb.toString();
    }

    /**
     * Sign with RSA private key.
     */
    private String signWithRsa(String data, String privateKey) {
        try {
            if (!StringUtils.hasText(privateKey)) {
                log.warn("Merchant private key is empty");
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
            log.error("RSA sign error", e);
            return "";
        }
    }

    /**
     * Verify with RSA public key.
     */
    private boolean verifyWithRsa(String data, String sign, String publicKey) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(publicKey);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PublicKey key = keyFactory.generatePublic(keySpec);

            Signature signature = Signature.getInstance(SIGN_ALGORITHM);
            signature.initVerify(key);
            signature.update(data.getBytes(StandardCharsets.UTF_8));
            return signature.verify(Base64.getDecoder().decode(sign));
        } catch (Exception e) {
            log.error("RSA verify error", e);
            return false;
        }
    }
}