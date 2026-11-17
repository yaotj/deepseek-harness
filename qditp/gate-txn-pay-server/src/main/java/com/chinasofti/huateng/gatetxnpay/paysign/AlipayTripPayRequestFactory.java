package com.chinasofti.huateng.gatetxnpay.paysign;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.gatetxnpay.entity.GateTxnPay;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestPayReqDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;


@Component
public class AlipayTripPayRequestFactory {

    private static final Logger log = LoggerFactory.getLogger(AlipayTripPayRequestFactory.class);

    public static final String PAY_TRIGGER_FIRST = "01";
    public static final String PAY_TRIGGER_COMPENSATION = "02";

    private final String scene;
    private final String paymentVendor;
    private final String industryType;
    private final String subject;
    private final String body;
    private final Integer orderTimeoutMinutes;

    public AlipayTripPayRequestFactory(
            @Value("${alipay.trip.scene:TRIP}") String scene,
            @Value("${alipay.trip.payment.vendor:05}") String paymentVendor,
            @Value("${alipay.trip.industry.type:1}") String industryType,
            @Value("${alipay.trip.subject:地铁乘车扣费}") String subject,
            @Value("${alipay.trip.body:地铁乘车费用}") String body,
            @Value("${alipay.trip.order.timeout.minutes:60}") Integer orderTimeoutMinutes) {
        this.scene = scene;
        this.paymentVendor = paymentVendor;
        this.industryType = industryType;
        this.subject = subject;
        this.body = body;
        this.orderTimeoutMinutes = orderTimeoutMinutes;
    }


    public AlipayTripRequestPayReqDTO build(GateTxnPay order, String payTriggerType) {
        AlipayTripRequestPayReqDTO payRequest = new AlipayTripRequestPayReqDTO();
        payRequest.setOrderNo(order.getOrderNo());
        payRequest.setScene(scene);
        payRequest.setPaymentVendor(paymentVendor);
        payRequest.setAmount(order.getTotalAmount());
        payRequest.setIndustryType(industryType);
        payRequest.setSubject(subject);
        payRequest.setBody(body);
        payRequest.setRequestSignSeq(order.getTicketTransSeq());
        payRequest.setThirdUserId(order.getThirdUserId());
        payRequest.setOrderTimeOut(orderTimeoutMinutes);
        payRequest.setIndustryDetail(applyPayTriggerType(order.getIndustryDetail(), payTriggerType, order.getOrderNo()));
        return payRequest;
    }


    static String applyPayTriggerType(String industryDetail, String payTriggerType, String orderNo) {
        if (!StringUtils.hasText(industryDetail)) {
            return industryDetail;
        }
        try {
            JSONObject detailJson = JSON.parseObject(industryDetail);
            detailJson.put("payTriggerType", payTriggerType);
            return detailJson.toJSONString();
        } catch (Exception e) {
            log.warn("industryDetail 解析失败，payTriggerType 未能注入、原样透传, orderNo={}, payTriggerType={}",
                    orderNo, payTriggerType, e);
            return industryDetail;
        }
    }
}
