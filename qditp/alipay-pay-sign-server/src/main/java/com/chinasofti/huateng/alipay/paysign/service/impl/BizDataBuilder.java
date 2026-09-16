package com.chinasofti.huateng.alipay.paysign.service.impl;

import com.chinasofti.huateng.alipay.paysign.config.PayCenterProperties;
import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfo;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripRequestPayReqDTO;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class BizDataBuilder {
    private static final Logger log = LoggerFactory.getLogger(BizDataBuilder.class);

    public Map<String, Object> build(
            AlipayTripRequestPayReqDTO request,
            AlipaySignInfo signInfo,
            PayCenterProperties payCenterProperties) {

        Map<String, Object> bizDataMap = new LinkedHashMap<>();
        bizDataMap.put("orderNo", request.getOrderNo());
        bizDataMap.put("scene", request.getScene());
        bizDataMap.put("paymentVendor", request.getPaymentVendor());
        bizDataMap.put("amount", request.getAmount());
        bizDataMap.put("industryType", request.getIndustryType());
        bizDataMap.put("subject", request.getSubject());
        bizDataMap.put("body", request.getBody());
        bizDataMap.put("requestSignSeq", signInfo.getAgreementCode());
        bizDataMap.put("thirdUserId", request.getThirdUserId());

        if (request.getOrderTimeOut() != null && request.getOrderTimeOut() > 0) {
            bizDataMap.put("orderTimeOut", request.getOrderTimeOut());
        } else {
            bizDataMap.put("orderTimeOut", 60);
        }
        putIfHasText(bizDataMap, "authCode", request.getAuthCode());
        putIfHasText(bizDataMap, "notifyUrl", request.getNotifyUrl());
        if (!StringUtils.hasText(request.getNotifyUrl()) && StringUtils.hasText(payCenterProperties.getCallbackUrl())) {
            bizDataMap.put("notifyUrl", payCenterProperties.getCallbackUrl());
        }
        putIfHasText(bizDataMap, "returnUrl", request.getReturnUrl());
        putIfHasText(bizDataMap, "ipAddress", request.getIpAddress());
        putIfHasText(bizDataMap, "remark", request.getRemark());

        bizDataMap.put("industryDetail", enrichIndustryDetail(request.getIndustryDetail(), signInfo.getChannelAgreementCode()));

        return bizDataMap;
    }

    private String enrichIndustryDetail(String industryDetail, String channelAgreementCode) {
        if (!StringUtils.hasText(industryDetail) || !StringUtils.hasText(channelAgreementCode)) {
            return industryDetail;
        }
        try {
            JSONObject detailJson = JSON.parseObject(industryDetail);
            detailJson.put("channelAgreementNo", channelAgreementCode);
            return detailJson.toJSONString();
        } catch (Exception e) {
            log.warn("industryDetail 解析失败，无法补充 channelAgreementNo, industryDetail={}", industryDetail, e);
            return industryDetail;
        }
    }

    private void putIfHasText(Map<String, Object> map, String key, String value) {
        if (StringUtils.hasText(value)) {
            map.put(key, value);
        }
    }
}
