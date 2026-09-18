package com.chinasofti.huateng.alipay.paysign.util;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.alipay.paysign.config.PayCenterProperties;
import com.chinasofti.huateng.alipay.paysign.model.request.PayCenterRequest;
import com.chinasofti.huateng.alipay.paysign.model.response.PayCenterResponse;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 支付中心 HTTP 调用客户端。
 *
 * <p>职责：封装与支付中心的所有交互，包括支付、退款、查询、通知等。
 * 当前签名采用测试占位，生产环境应替换为真实 RSA 签名流程。
 */
@Component
public class PayCenterClient {
    private static final Logger log = LoggerFactory.getLogger(PayCenterClient.class);
    private static final MediaType MEDIA_TYPE_JSON = MediaType.parse("application/json; charset=utf-8");
    private static final String SIGN_ALGORITHM = "SHA256WithRSA";
    private static final int DEFAULT_ORDER_TIMEOUT = 60;

    private final OkHttpClient httpClient;
    private final PayCenterProperties payCenterProperties;

    @Autowired
    public PayCenterClient(PayCenterProperties payCenterProperties) {
        this.payCenterProperties = payCenterProperties;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 调用支付中心支付接口。
     */
    public PayCenterResponse requestPay(Map<String, Object> bizDataMap) {
        return callPayCenter(payCenterProperties.getRequestPayUrl(), buildEncryptedRequest(bizDataMap));
    }

    /**
     * 调用支付宝退款接口。
     */
    public PayCenterResponse requestRefund(Map<String, Object> bizDataMap) {
        return callPayCenter(payCenterProperties.getRequestRefundUrl(), buildEncryptedRequest(bizDataMap));
    }

    /**
     * 调用支付中心支付查询接口。
     */
    public PayCenterResponse payQuery(Map<String, Object> bizDataMap) {
        return callPayCenter(payCenterProperties.getPayQueryUrl(), buildEncryptedRequest(bizDataMap));
    }

    /**
     * 调用支付中心退款查询接口。
     */
    public PayCenterResponse refundQuery(Map<String, Object> bizDataMap) {
        return callPayCenter(payCenterProperties.getRefundQueryUrl(), buildEncryptedRequest(bizDataMap));
    }

    /**
     * 调用支付中心业务关闭结果通知接口。
     */
    public PayCenterResponse closeResultNotify(Map<String, Object> bizDataMap) {
        return callPayCenter(payCenterProperties.getCloseResultNotifyUrl(), buildPlainRequest(bizDataMap));
    }

    /**
     * 调用支付中心黑名单变更通知接口。
     */
    public PayCenterResponse blacklistNotify(Map<String, Object> bizDataMap) {
        return callPayCenter(payCenterProperties.getBlacklistNotifyUrl(), buildPlainRequest(bizDataMap), true);
    }

    /**
     * 构建支付中心请求，bizData 采用 Base64 编码。
     */
    private PayCenterRequest buildEncryptedRequest(Map<String, Object> bizDataMap) {
        return buildRequest(bizDataMap, true);
    }

    /**
     * 构建支付中心请求，bizData 采用 Base64 编码，并指定接口路径。
     */
    private PayCenterRequest buildEncryptedRequest(String path, Map<String, Object> bizDataMap) {
        return buildRequest(path, bizDataMap, true);
    }

    /**
     * 构建支付中心请求，bizData 保持明文。
     */
    private PayCenterRequest buildPlainRequest(Map<String, Object> bizDataMap) {
        return buildRequest(bizDataMap, false);
    }

    /**
     * 统一构建支付中心请求。
     *
     * @param bizDataMap 业务数据
     * @param encrypted  是否对 bizData 做 Base64 编码
     */
    private PayCenterRequest buildRequest(Map<String, Object> bizDataMap, boolean encrypted) {
        PayCenterRequest payCenterRequest = new PayCenterRequest();
        payCenterRequest.setMerchantNo(payCenterProperties.getMerchantNo());
        payCenterRequest.setApiVersion(payCenterProperties.getApiVersion());
        payCenterRequest.setSignType(payCenterProperties.getSignType());
        payCenterRequest.setCharset(payCenterProperties.getCharset());
        String bizDataJson = JSON.toJSONString(bizDataMap);
        payCenterRequest.setBizData(encrypted
                ? Base64.getEncoder().encodeToString(bizDataJson.getBytes(StandardCharsets.UTF_8))
                : bizDataJson);
        log.info("请求支付中心({}), bizData={}", encrypted ? "加密" : "明文", bizDataJson);
        signRequest(payCenterRequest);
        return payCenterRequest;
    }

    /**
     * 统一构建支付中心请求，并指定接口路径。
     */
    private PayCenterRequest buildRequest(String path, Map<String, Object> bizDataMap, boolean encrypted) {
        return buildRequest(bizDataMap, encrypted);
    }

    /**
     * 统一调用支付中心 HTTP 接口。
     */
    private PayCenterResponse callPayCenter(String url, PayCenterRequest request) {
        return callPayCenter(url, request, false);
    }

    /**
     * 统一调用支付中心 HTTP 接口。
     *
     * @param url      目标地址
     * @param request  请求体
     * @param formMode 是否以 form-data 发送
     */
    private PayCenterResponse callPayCenter(String url, PayCenterRequest request, boolean formMode) {
        if (url == null || url.trim().isEmpty()) {
            log.error("支付中心接口地址未配置");
            return null;
        }
        try {
            String jsonBody = JSON.toJSONString(request);
            log.info("调用支付中心, url={}, request={}", url, jsonBody);

            RequestBody body;
            Request httpRequest;
            if (formMode) {
                MultipartBody.Builder formBuilder = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM);
                if (request.getMerchantNo() != null) {
                    formBuilder.addFormDataPart("merchantNo", request.getMerchantNo());
                }
                if (request.getApiVersion() != null) {
                    formBuilder.addFormDataPart("apiVersion", request.getApiVersion());
                }
                if (request.getSignType() != null) {
                    formBuilder.addFormDataPart("signType", request.getSignType());
                }
                if (request.getCharset() != null) {
                    formBuilder.addFormDataPart("charset", request.getCharset());
                }
                if (request.getBizData() != null) {
                    formBuilder.addFormDataPart("bizData", request.getBizData());
                }
                if (request.getSign() != null) {
                    formBuilder.addFormDataPart("sign", request.getSign());
                }
                body = formBuilder.build();
            } else {
                body = RequestBody.create(jsonBody, MEDIA_TYPE_JSON);
            }

            httpRequest = new Request.Builder()
                    .url(url)
                    .post(body)
                    .build();

            try (Response httpResponse = httpClient.newCall(httpRequest).execute()) {
                if (httpResponse.isSuccessful() && httpResponse.body() != null) {
                    String responseBody = httpResponse.body().string();
                    log.info("支付中心响应, response={}", responseBody);
                    return JSON.parseObject(responseBody, PayCenterResponse.class);
                } else {
                    log.error("调用支付中心失败, code={}, message={}",
                            httpResponse.code(), httpResponse.message());
                    return null;
                }
            }
        } catch (IOException e) {
            log.error("调用支付中心异常", e);
            return null;
        }
    }

    /**
     * 出向报文签名。当前是占位实现（{@code sign="test"}），上线前 MUST 替换为真实签名。
     */
    private void signRequest(PayCenterRequest request) {
        log.warn("测试阶段使用测试签名");
        request.setSign("test");
    }

    /**
     * 构建签名源数据。
     */
    private String buildSignData(PayCenterRequest request) {
        Map<String, String> params = new java.util.LinkedHashMap<>();
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

    /**
     * RSA签名。
     */
    private String signWithRsa(String data, String privateKey) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(privateKey);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PrivateKey priKey = keyFactory.generatePrivate(keySpec);

        Signature signature = Signature.getInstance(SIGN_ALGORITHM);
        signature.initSign(priKey);
        signature.update(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signature.sign());
    }

    /**
     * 生成商户订单号。
     */
    public String generateMerchantOrderNo() {
        return "M" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 把响应的 data 解开成 map，**整个响应只解一次**。
     *
     * <p>{@link #getStringFromData} 与 {@link #getIntFromData} 每调一次都重新 Base64 + JSON 解析一遍，
     * 一次支付查询要取 6 个字段就解 6 遍。{@code PayCenterRpcAdapter} 用本方法一次解开、装进
     * {@code PayCenterReply.Accepted.data}（ADR-D131），<b>NEVER 在调用点回退成逐键 getStringFromData</b>。
     * 那两个逐键方法保留是因为仍有其它调用点，本批次不动它们。</p>
     *
     * @return 解不开或 data 为空时返回**空 map、不是 null**（调用点是 record 字段，不该再判空）
     */
    public Map<String, Object> decodeDataMap(PayCenterResponse response) {
        if (response == null || response.getData() == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> dataMap = decodeData(response.getData());
        return dataMap == null ? Collections.emptyMap() : dataMap;
    }

    /**
     * 从响应数据中获取字符串值。
     */
    public String getStringFromData(PayCenterResponse response, String key) {
        if (response == null || response.getData() == null) {
            return null;
        }
        Map<String, Object> dataMap = decodeData(response.getData());
        if (dataMap == null || !dataMap.containsKey(key)) {
            return null;
        }
        Object value = dataMap.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * 从响应数据中获取整数值。
     */
    public Integer getIntFromData(PayCenterResponse response, String key) {
        if (response == null || response.getData() == null) {
            return null;
        }
        Map<String, Object> dataMap = decodeData(response.getData());
        if (dataMap == null || !dataMap.containsKey(key)) {
            return null;
        }
        Object value = dataMap.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Base64解码响应数据。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> decodeData(String base64Data) {
        try {
            byte[] decodedBytes = Base64.getDecoder().decode(base64Data);
            String json = new String(decodedBytes, StandardCharsets.UTF_8);
            return JSON.parseObject(json, Map.class);
        } catch (Exception e) {
            log.error("Base64解码响应数据异常", e);
            return null;
        }
    }
}
