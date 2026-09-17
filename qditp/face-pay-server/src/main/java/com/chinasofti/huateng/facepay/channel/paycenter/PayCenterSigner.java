package com.chinasofti.huateng.facepay.channel.paycenter;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** 支付中心签名。 */
public class PayCenterSigner {

    private final String privateKeyBase64;

    private final String signAlgorithm;

    private final String aggregateCodeKey;

    public PayCenterSigner(String privateKeyBase64, String signAlgorithm, String aggregateCodeKey) {
        this.privateKeyBase64 = privateKeyBase64;
        this.signAlgorithm = signAlgorithm;
        this.aggregateCodeKey = aggregateCodeKey;
    }

    /**
     * 对信封签名，写回 {@code sign} 字段。
     *
     * @param bizDataJson Base64 之前的原始 JSON。待签串取自它的内部字段，
     */
    public void sign(PayCenterRequest request, String bizDataJson) {
        if (privateKeyBase64 == null || privateKeyBase64.isBlank()) {
            throw new IllegalStateException("pay.center.private-key 未配置，无法签名");
        }
        request.setSign(rsaSign(buildSignSource(bizDataJson)));
    }

    /**
     * 待签串组装。
     *
     * @param bizDataJson Base64 之前的原始 JSON
     */
    public static String buildSignSource(String bizDataJson) {
        if (bizDataJson == null || bizDataJson.isBlank()) {
            throw new IllegalArgumentException("bizDataJson 为空，无法组装待签串");
        }
        Map<String, Object> params = JSON.parseObject(bizDataJson, TreeMap.class);
        StringBuilder source = new StringBuilder();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            Object value = entry.getValue();
            if (value == null || String.valueOf(value).isBlank()) {
                continue;
            }
            if (!source.isEmpty()) {
                source.append('&');
            }
            source.append(entry.getKey()).append('=').append(signValue(value));
        }
        return source.toString();
    }

    /** 嵌套对象/数组按键排序后序列化，与 {@code PayGatewayClient.signValue} 一致。 */
    private static String signValue(Object value) {
        return value instanceof Map || value instanceof List
                ? JSON.toJSONString(value, JSONWriter.Feature.MapSortField)
                : String.valueOf(value);
    }

    /** 聚合码 URL 的签名：{@code md5("orderNo=" + orderNo + "&key=" + jhmKey)}。 */
    public String aggregateCodeSign(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            throw new IllegalArgumentException("orderNo 为空，无法生成聚合码签名");
        }
        if (aggregateCodeKey == null || aggregateCodeKey.isBlank()) {
            throw new IllegalStateException("pay.center.jhm-key 未配置，无法生成聚合码签名");
        }
        return md5Hex("orderNo=" + orderNo + "&key=" + aggregateCodeKey);
    }

    private String rsaSign(String source) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(privateKeyBase64);
            PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
            Signature signature = Signature.getInstance(signAlgorithm);
            signature.initSign(key);
            signature.update(source.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception e) {
            throw new IllegalStateException("支付中心报文签名失败, algorithm=" + signAlgorithm, e);
        }
    }

    private static String md5Hex(String source) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("MD5")
                    .digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("聚合码签名计算失败", e);
        }
    }
}
