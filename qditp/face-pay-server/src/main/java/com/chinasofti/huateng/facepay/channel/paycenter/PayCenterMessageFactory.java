package com.chinasofti.huateng.facepay.channel.paycenter;

import com.alibaba.fastjson2.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

public class PayCenterMessageFactory {

    private static final Logger log = LoggerFactory.getLogger(PayCenterMessageFactory.class);

    private final PayCenterProperties properties;

    private final PayCenterSigner signer;

    public PayCenterMessageFactory(PayCenterProperties properties, PayCenterSigner signer) {
        this.properties = properties;
        this.signer = signer;
    }

    public PayCenterRequest buildPayRequest(PayCenterPayCommand command) {
        return buildPayRequest(command, properties.getPayNoticeUrl());
    }

    public PayCenterRequest buildPayRequest(PayCenterPayCommand command, String notifyUrl) {
        Map<String, Object> bizData = new LinkedHashMap<>();
        bizData.put("orderNo", command.orderNo());
        bizData.put("scene", command.scene().getCode());
        bizData.put("paymentVendor", command.effectivePaymentVendor());
        if (command.scene() != PayScene.SCAN && command.payType() != null) {
            bizData.put("payType", command.payType());
        }
        bizData.put("amount", command.amount());
        bizData.put("industryType", properties.getIndustryType());
        bizData.put("subject", command.subject());
        bizData.put("body", command.body());
        bizData.put("orderTimeOut", properties.getOrderTimeOutSeconds());
        if (command.scene() == PayScene.SCAN) {
            bizData.put("authCode", command.authCode());
        }
        bizData.put("notifyUrl", notifyUrl);
        return envelope(bizData);
    }

    public PayCenterRequest buildQueryRequest(String orderNo) {
        Map<String, Object> bizData = new LinkedHashMap<>();
        bizData.put("merchantOrderNo", orderNo);
        return envelope(bizData);
    }

    public PayCenterRequest buildRefundRequest(String refundNo, String origOrderNo,
                                               String payCenterOrderNo, long refundAmount) {
        if (refundAmount <= 0) {
            throw new IllegalArgumentException("refundAmount 必须为正数，单位分, refundNo=" + refundNo);
        }
        Map<String, Object> bizData = new LinkedHashMap<>();
        bizData.put("refundOrderNo", refundNo);
        bizData.put("merchantOrderNo", origOrderNo);
        bizData.put("orderNo", payCenterOrderNo);
        bizData.put("refundAmount", refundAmount);
        bizData.put("refundReason", properties.getRefundReason());
        String refundNoticeUrl = properties.getRefundNoticeUrl();
        if (refundNoticeUrl != null && !refundNoticeUrl.isBlank()) {
            log.info("退款申请携带 notifyUrl={}，支付中心将回推退款结果至此, refundNo={}, origOrderNo={}",
                    refundNoticeUrl, refundNo, origOrderNo);
            bizData.put("notifyUrl", refundNoticeUrl);
        } else {
            log.warn("pay.center.refund-notice-url 未配置，本次退款申请不送 notifyUrl，"
                    + "支付中心将无法回推退款结果，只能靠退款回查收口, refundNo={}, origOrderNo={}",
                    refundNo, origOrderNo);
        }
        return envelope(bizData);
    }

    public PayCenterRequest buildRefundQueryRequest(String refundNo) {
        Map<String, Object> bizData = new LinkedHashMap<>();
        bizData.put("merchantRefundNo", refundNo);
        return envelope(bizData);
    }

    public String buildAggregateCodePayUrl(String orderNo) {
        String base = properties.getCheckoutCounterUrl();
        if (base == null || base.isBlank()) {
            throw new IllegalStateException("pay.center.checkout-counter-url 未配置，payType=0 无法出码");
        }
        return base + "?orderNo=" + orderNo + "&sign=" + signer.aggregateCodeSign(orderNo);
    }

    private PayCenterRequest envelope(Map<String, Object> bizData) {
        PayCenterRequest request = new PayCenterRequest();
        request.setMerchantNo(properties.getMerchantNo());
        request.setApiVersion(properties.getApiVersion());
        request.setSignType(properties.getSignType());
        request.setCharset(properties.getCharset());
        String bizDataJson = JSON.toJSONString(bizData);
        request.setBizData(Base64.getEncoder()
                .encodeToString(bizDataJson.getBytes(StandardCharsets.UTF_8)));
        signer.sign(request, bizDataJson);
        return request;
    }
}
