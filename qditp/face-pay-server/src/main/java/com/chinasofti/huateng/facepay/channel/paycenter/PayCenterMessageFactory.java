package com.chinasofti.huateng.facepay.channel.paycenter;

import com.alibaba.fastjson2.JSON;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** 支付中心报文组装。 */
public class PayCenterMessageFactory {

    private final PayCenterProperties properties;

    private final PayCenterSigner signer;

    public PayCenterMessageFactory(PayCenterProperties properties, PayCenterSigner signer) {
        this.properties = properties;
        this.signer = signer;
    }

    /** 预下单。 */
    public PayCenterRequest buildPayRequest(PayCenterPayCommand command) {
        return buildPayRequest(command, properties.getPayNoticeUrl());
    }

    /** 预下单，按笔指定支付结果回调地址（网关文档 §支付下单：{@code notifyUrl} 可选、不传取商户默认配置）。 */
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

    /** 支付结果查询。 */
    public PayCenterRequest buildQueryRequest(String orderNo) {
        Map<String, Object> bizData = new LinkedHashMap<>();
        bizData.put("merchantOrderNo", orderNo);
        return envelope(bizData);
    }

    /** 退款。 */
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
        return envelope(bizData);
    }

    /** 退款结果查询。 */
    public PayCenterRequest buildRefundQueryRequest(String refundNo) {
        Map<String, Object> bizData = new LinkedHashMap<>();
        bizData.put("merchantRefundNo", refundNo);
        return envelope(bizData);
    }

    /** {@code payType=0} 的本地分支：不调支付中心，把聚合码收银台 URL 拼出来直接返给设备， 设备把整串显示成二维码、不解析内容。 */
    public String buildAggregateCodePayUrl(String orderNo) {
        String base = properties.getCheckoutCounterUrl();
        if (base == null || base.isBlank()) {
            throw new IllegalStateException("pay.center.checkout-counter-url 未配置，payType=0 无法出码");
        }
        return base + "?orderNo=" + orderNo + "&sign=" + signer.aggregateCodeSign(orderNo);
    }

    /** 组装信封并签名。 */
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
