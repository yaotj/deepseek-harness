package com.chinasofti.huateng.gatetxnpay.paysign;

import com.chinasofti.huateng.gatetxnpay.entity.GateTxnPay;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestPayReqDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 支付宝出行免密扣费报文的唯一组装处，含 {@code alipay.trip.*} 六个配置项与回调地址。 */
@Component
public class AlipayTripPayRequestFactory {

    private final String scene;
    private final String paymentVendor;
    private final String industryType;
    private final String subject;
    private final String body;
    private final Integer orderTimeoutMinutes;
    private final String payCallbackUrl;

    public AlipayTripPayRequestFactory(
            @Value("${alipay.trip.scene:TRIP}") String scene,
            @Value("${alipay.trip.payment.vendor:05}") String paymentVendor,
            @Value("${alipay.trip.industry.type:1}") String industryType,
            @Value("${alipay.trip.subject:地铁乘车扣费}") String subject,
            @Value("${alipay.trip.body:地铁乘车费用}") String body,
            @Value("${alipay.trip.order.timeout.minutes:60}") Integer orderTimeoutMinutes,
            @Value("${pay.center.callback-url:}") String payCallbackUrl) {
        this.scene = scene;
        this.paymentVendor = paymentVendor;
        this.industryType = industryType;
        this.subject = subject;
        this.body = body;
        this.orderTimeoutMinutes = orderTimeoutMinutes;
        this.payCallbackUrl = payCallbackUrl;
    }

    /** 组装支付宝出行免密扣费报文。 */
    public AlipayTripRequestPayReqDTO build(GateTxnPay order) {
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
        payRequest.setNotifyUrl(payCallbackUrl);
        payRequest.setIndustryDetail(order.getIndustryDetail());
        return payRequest;
    }
}
