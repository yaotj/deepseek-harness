package com.chinasofti.huateng.dailyticket.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.json.JSONUtil;
import com.chinasofti.huateng.micro.web.client.ProxyWebClient;
import com.chinasofti.huateng.dailyticket.config.DailyTicketPayProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 日票专用支付网关客户端。
 *
 * <p>该客户端绕开pay-sign-server的签约卡支付封装，避免影响原有通用支付接口。</p>
 */
@Service
public class DailyTicketPayGatewayClient extends ProxyWebClient {
    private final DailyTicketPayProperties properties;

    public DailyTicketPayGatewayClient(DailyTicketPayProperties properties, WebClient.Builder webClientBuilder) {
        super(resolveGatewayUrl(properties), true, webClientBuilder);
        this.properties = properties;
    }

    /**
     * 发起日票支付。
     */
    public DailyTicketPayGatewayResponse requestPay(Map<String, Object> bizData) {
        String result = postJsonAndGetResponse(properties.getRequestPayPath(), buildGatewayRequest(bizData));
        return JSONUtil.toBean(result, new TypeReference<DailyTicketPayGatewayResponse>() {
        }, true);
    }

    /**
     * 发起日票退款。
     */
    public DailyTicketPayGatewayResponse requestRefund(Map<String, Object> bizData) {
        String result = postJsonAndGetResponse(properties.getRequestRefundPath(), buildGatewayRequest(bizData));
        return JSONUtil.toBean(result, new TypeReference<DailyTicketPayGatewayResponse>() {
        }, true);
    }

    private DailyTicketPayGatewayRequest buildGatewayRequest(Map<String, Object> bizData) {
        DailyTicketPayGatewayRequest request = new DailyTicketPayGatewayRequest();
        request.setMerchantNo(properties.getMerchantNo());
        request.setApiVersion(properties.getApiVersion());
        request.setSignType(properties.getSignType());
        request.setCharset(properties.getCharset());
        String bizDataJson = JSON.toJSONString(bizData);
        request.setBizData(Base64.getEncoder().encodeToString(bizDataJson.getBytes(StandardCharsets.UTF_8)));
        request.setSign(sign(buildGatewayBizSignSource(bizDataJson)));
        return request;
    }

    private String buildGatewayBizSignSource(String bizDataJson) {
        Map<String, Object> params = JSON.parseObject(bizDataJson, TreeMap.class);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (entry.getValue() == null || !StringUtils.hasText(String.valueOf(entry.getValue()))) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("&");
            }
            sb.append(entry.getKey()).append("=").append(gatewaySignValue(entry.getValue()));
        }
        return sb.toString();
    }

    private String gatewaySignValue(Object value) {
        if (value instanceof Map || value instanceof List) {
            return JSON.toJSONString(value, JSONWriter.Feature.MapSortField);
        }
        return String.valueOf(value);
    }

    private String sign(String plainText) {
        if (!StringUtils.hasText(properties.getMerchantPrivateKey())) {
            return "";
        }
        try {
            byte[] priKeyText = properties.getMerchantPrivateKey().getBytes(StandardCharsets.UTF_8);
            PKCS8EncodedKeySpec priPKCS8 = new PKCS8EncodedKeySpec(Base64.getDecoder().decode(priKeyText));
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PrivateKey privateKey = keyFactory.generatePrivate(priPKCS8);
            Signature signature = Signature.getInstance("SHA256WithRSA");
            signature.initSign(privateKey);
            signature.update(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception e) {
            return "";
        }
    }

    private static String resolveGatewayUrl(DailyTicketPayProperties properties) {
        if (properties != null && StringUtils.hasText(properties.getGatewayUrl())) {
            return properties.getGatewayUrl();
        }
        return "http://127.0.0.1:9096";
    }
}
