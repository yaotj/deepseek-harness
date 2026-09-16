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
 *
 * <p>2026-09-11 由 {@link EmployeeCardServiceImpl} 逐字搬迁而来（account-server 2.0.58）：
 * 方法体、日志文案与注释一并保留，<b>没有改任何行为</b>。搬过来的是
 * {@code postFormData} / {@code registerEmployeeCardsToApp} / {@code parseAppFailList}
 * / {@code queryEmployeeCardFromAcc} 与它们独占的配置项 + {@code RestTemplate}。</p>
 *
 * <p><b>NEVER 在本类里加事务</b> —— 这里只有 HTTP，没有一条 SQL；反过来，
 * <b>调用方也 NEVER 把本类的方法放进 {@code @Transactional} 里</b>（AGENTS.md §5.2：
 * 事务包住 RPC 已在 2026-08-26 造成生产事故）。</p>
 */
@Service
public class EmployeeCardOutboundServiceImpl implements EmployeeCardOutboundService {
    private static final Logger log = LoggerFactory.getLogger(EmployeeCardOutboundServiceImpl.class);
    private static final DateTimeFormatter REQUEST_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final RestTemplate restTemplate;

    /**
     * 出网地址与报文常量。2026-09-11 由 10 个散落的 {@code @Value} 收成一个对象（配置键未变），
     * 见 {@link EmployeeCardOutboundProperties}。<b>NEVER 再往本类加单独的 {@code @Value} 字段</b>，
     * 新配置项一律加到那个类里，否则又会散回来。
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
                // 响应不是 JSON 对象（纯文本 / JSON 数组 / null 字面量）时 parseObject 返回 null，
                // 直接 getString 会 NPE 并被下方 catch 吞成「远端业务拒绝」，整批失败原因不可辨。
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
            // MUST 保留 hasText 前置：ACC 有的响应不带任何码，本方法的历史语义是「没回码就当成功」。
            // NEVER 简化成 !AccResultCode.isSuccess(resultCode) —— 那会把「没回码」从放行改成拒绝。
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
        // 与 registerToApp / queryFromAcc 同口径先判地址：未配置时 RestTemplate 抛的是无业务语义的
        // IllegalArgumentException，调用方看不出「是没配地址」还是「ACC 拒绝了」。
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
