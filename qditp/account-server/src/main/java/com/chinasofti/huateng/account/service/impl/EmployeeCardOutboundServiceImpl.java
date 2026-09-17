package com.chinasofti.huateng.account.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.account.domain.AccResultCode;
import com.chinasofti.huateng.account.model.EmployeeCardOutboundProperties;
import com.chinasofti.huateng.account.service.EmployeeCardOutboundService;
import com.chinasofti.huateng.model.employee.EmployeeCardActivateReqDTO;
import com.chinasofti.huateng.model.employee.EmployeeCardInfoDTO;
import com.chinasofti.huateng.model.employee.EmployeeCardQueryReqDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 员工码出网实现。
 */
@Service
public class EmployeeCardOutboundServiceImpl implements EmployeeCardOutboundService {
    private static final Logger log = LoggerFactory.getLogger(EmployeeCardOutboundServiceImpl.class);
    private static final DateTimeFormatter REQUEST_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final RestTemplate restTemplate;

    /**
     * 出网地址与报文常量。
     */
    private final EmployeeCardOutboundProperties properties;

    public EmployeeCardOutboundServiceImpl(RestTemplateBuilder restTemplateBuilder,
                                          EmployeeCardOutboundProperties properties) {
        this.properties = properties;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                .build();
    }

    @Override
    public AppRegisterResult registerToApp(List<EmployeeCardInfoDTO> batch) {
        if (!StringUtils.hasText(properties.getAppRegisterUrl())) {
            return wholeBatchFailure(batch, "APP注册接口地址未配置");
        }
        try {
            String response = postFormData(properties.getAppRegisterUrl(), Collections.singletonMap("cardList", batch));
            if (!StringUtils.hasText(response)) {
                return wholeBatchFailure(batch, "APP注册接口无响应");
            }
            JSONObject body = JSON.parseObject(response);
            if (body == null) {
                log.error("APP注册接口响应非JSON对象, batchSize={}, response={}", batch.size(), response);
                return wholeBatchFailure(batch, "APP注册接口响应非JSON");
            }
            String resultCode = body.getString("retCode");
            if (!StringUtils.hasText(resultCode)) {
                resultCode = body.getString("code");
            }
            if (AccResultCode.isSuccess(resultCode)) {
                return new AppRegisterResult(Collections.emptyMap());
            }
            String resultMessage = body.getString("retMsg");
            if (!StringUtils.hasText(resultMessage)) {
                resultMessage = body.getString("msg");
            }
            if ("0001".equals(resultCode)) {
                Map<String, String> failReasons = parseAppFailList(body, resultMessage);
                if (!failReasons.isEmpty()) {
                    return new AppRegisterResult(failReasons);
                }
            }
            return wholeBatchFailure(batch,
                    StringUtils.hasText(resultMessage) ? resultMessage : "APP注册接口返回失败");
        } catch (RuntimeException ex) {
            log.error("调用APP注册员工码失败, batchSize={}", batch.size(), ex);
            return wholeBatchFailure(batch, "调用APP注册接口失败");
        }
    }

    private AppRegisterResult wholeBatchFailure(List<EmployeeCardInfoDTO> batch, String message) {
        Map<String, String> failReasons = new HashMap<>();
        for (EmployeeCardInfoDTO card : batch) {
            failReasons.put(card.getCardNo(), message);
        }
        return new AppRegisterResult(failReasons);
    }

    private Map<String, String> parseAppFailList(JSONObject body, String defaultReason) {
        JSONArray failList = body.getJSONArray("failList");
        if (failList == null || failList.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> failReasons = new HashMap<>();
        for (int i = 0; i < failList.size(); i++) {
            JSONObject item = failList.getJSONObject(i);
            if (item == null || !StringUtils.hasText(item.getString("cardNo"))) {
                continue;
            }
            String reason = item.getString("reason");
            if (!StringUtils.hasText(reason)) {
                reason = StringUtils.hasText(defaultReason) ? defaultReason : "APP注册接口返回失败";
            }
            failReasons.put(item.getString("cardNo"), reason);
        }
        return failReasons;
    }

    @Override
    public EmployeeCardInfoDTO queryFromAcc(String cardNo) {
        if (!StringUtils.hasText(properties.getAccQueryUrl())) {
            log.warn("ACC员工码查询地址未配置, cardNo={}", cardNo);
            return null;
        }
        try {
            EmployeeCardQueryReqDTO request = new EmployeeCardQueryReqDTO();
            request.setCardNo(cardNo);
            String response = postFormData(properties.getAccQueryUrl(), request);
            if (!StringUtils.hasText(response)) {
                return null;
            }
            JSONObject body = JSON.parseObject(response);
            if (body == null) {
                log.error("ACC员工码查询响应非JSON对象, cardNo={}, response={}", cardNo, response);
                return null;
            }
            String resultCode = body.getString("retCode");
            if (!StringUtils.hasText(resultCode)) {
                resultCode = body.getString("code");
            }
            if (StringUtils.hasText(resultCode) && !AccResultCode.isSuccess(resultCode)) {
                log.warn("ACC员工码查询返回失败, cardNo={}, response={}", cardNo, response);
                return null;
            }
            Object bizData = body.get("bizData");
            if (bizData instanceof JSONObject jsonObject) {
                return jsonObject.toJavaObject(EmployeeCardInfoDTO.class);
            }
            if (bizData instanceof String json && StringUtils.hasText(json)) {
                return JSON.parseObject(json, EmployeeCardInfoDTO.class);
            }
            return body.toJavaObject(EmployeeCardInfoDTO.class);
        } catch (RuntimeException ex) {
            log.error("调用ACC查询员工码信息失败, cardNo={}", cardNo, ex);
            return null;
        }
    }

    @Override
    public boolean isActivationUrlConfigured() {
        return StringUtils.hasText(properties.getAccActivateUrl());
    }

    @Override
    public String requestActivation(EmployeeCardActivateReqDTO request) {
        if (!isActivationUrlConfigured()) {
            throw new IllegalStateException("ACC员工码激活接口地址未配置");
        }
        return postFormData(properties.getAccActivateUrl(), request);
    }

    private String postFormData(String url, Object bizData) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("providerId", properties.getProviderId());
        formData.add("charset", properties.getCharset());
        formData.add("format", properties.getFormat());
        formData.add("timestamp", LocalDateTime.now().format(REQUEST_TIME_FORMATTER));
        formData.add("deviceId", properties.getDeviceId());
        formData.add("signType", properties.getSignType());
        formData.add("sign", "");
        formData.add("bizData", JSON.toJSONString(bizData));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return restTemplate.postForObject(url, new HttpEntity<>(formData, headers), String.class);
    }
}
