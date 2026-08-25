package com.chinasofti.huateng.paysign.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.chinasofti.huateng.paysign.exception.PayGatewayException;
import com.chinasofti.huateng.paysign.config.PaySignProperties;
import com.chinasofti.huateng.paysign.model.request.PaySignGatewayRequest;
import com.chinasofti.huateng.paysign.model.response.PaySignGatewayResponse;
import com.chinasofti.huateng.paysign.util.RSASignUtils;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/**
 * 支付网关适配器。
 *
 * <p>负责支付网关公共报文组装、签名和 HTTP 通信；领域服务只能传入接口路径和业务参数，
 * 不感知 OkHttp、Base64 编码或签名字段。这样支付平台协议变化只会影响此类。</p>
 */
@Component
public class PayGatewayClient {
    private static final Logger log = LoggerFactory.getLogger(PayGatewayClient.class);
    private static final MediaType MEDIA_TYPE_JSON = MediaType.parse("application/json; charset=utf-8");
    private static final Integer SUCCESS_CODE = 200;

    private final PaySignProperties properties;
    private final OkHttpClient httpClient;

    public PayGatewayClient(PaySignProperties properties) {
        this.properties = properties;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 调用支付网关。传输或协议异常以 {@code PayGatewayException} 抛出，由调用方处理。
     */
    public PaySignGatewayResponse request(String path, Map<String, Object> bizData) {
        if (!StringUtils.hasText(properties.getGatewayUrl())) {
            throw new PayGatewayException("支付网关地址未配置, path=" + path);
        }
        try {
            String bizDataJson = JSON.toJSONString(bizData);
            PaySignGatewayRequest gatewayRequest = buildRequest(bizDataJson);
            String url = properties.getGatewayUrl() + path;
            Request httpRequest = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(JSON.toJSONString(gatewayRequest), MEDIA_TYPE_JSON))
                    .build();

            try (Response httpResponse = httpClient.newCall(httpRequest).execute()) {
                if (!httpResponse.isSuccessful() || httpResponse.body() == null) {
                    throw new PayGatewayException("调用支付网关失败, path=" + path + ", httpCode=" + httpResponse.code());
                }
                return JSON.parseObject(httpResponse.body().string(), PaySignGatewayResponse.class);
            }
        } catch (PayGatewayException e) {
            throw e;
        } catch (Exception e) {
            throw new PayGatewayException("调用支付网关异常, path=" + path, e);
        }
    }

    /** 判断支付网关标准成功码。 */
    public boolean isSuccess(PaySignGatewayResponse response) {
        return response != null && (Integer.valueOf(0).equals(response.getCode()) || SUCCESS_CODE.equals(response.getCode()));
    }

    /** 返回网关错误信息；网关无可用错误时使用调用方提供的语义化默认文案。 */
    public String errorMessage(PaySignGatewayResponse response, String defaultMessage) {
        return response == null || !StringUtils.hasText(response.getMsg()) ? defaultMessage : response.getMsg();
    }

    private PaySignGatewayRequest buildRequest(String bizDataJson) throws Exception {
        PaySignGatewayRequest request = new PaySignGatewayRequest();
        request.setMerchantNo(properties.getMerchantNo());
        request.setApiVersion(properties.getApiVersion());
        request.setSignType(properties.getSignType());
        request.setCharset(properties.getCharset());
        request.setBizData(Base64.getEncoder().encodeToString(bizDataJson.getBytes(StandardCharsets.UTF_8)));
        request.setSign(sign(bizDataJson));
        return request;
    }

    private String sign(String bizDataJson) throws Exception {
        String privateKey = properties.getMerchantPrivateKey();
        if (!StringUtils.hasText(privateKey)) {
            throw new IllegalStateException("pay.sign.merchant-private-key 未配置");
        }
        return RSASignUtils.sign(privateKey, buildSignSource(bizDataJson));
    }

    private String buildSignSource(String bizDataJson) {
        Map<String, Object> params = JSON.parseObject(bizDataJson, TreeMap.class);
        StringBuilder source = new StringBuilder();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (entry.getValue() == null || !StringUtils.hasText(String.valueOf(entry.getValue()))) {
                continue;
            }
            if (!source.isEmpty()) {
                source.append('&');
            }
            source.append(entry.getKey()).append('=').append(signValue(entry.getValue()));
        }
        return source.toString();
    }

    private String signValue(Object value) {
        return value instanceof Map || value instanceof List
                ? JSON.toJSONString(value, JSONWriter.Feature.MapSortField)
                : String.valueOf(value);
    }
}
