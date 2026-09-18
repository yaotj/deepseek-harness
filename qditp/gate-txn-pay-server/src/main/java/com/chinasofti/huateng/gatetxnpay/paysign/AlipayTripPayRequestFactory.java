package com.chinasofti.huateng.gatetxnpay.paysign;

import com.chinasofti.huateng.gatetxnpay.entity.GateTxnPay;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestPayReqDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 支付宝出行免密扣费报文的唯一组装处，含 {@code alipay.trip.*} 六个配置项。
 *
 * <p><b>本类 NEVER 设 {@code notifyUrl}</b>（2026-09-18 移除）：回调地址收口到 alipay-pay-sign-server
 * 一处的 {@code pay.center.callback-url}，由它的 {@code BizDataBuilder} 在「调用方没传 notifyUrl」时兜底填入。
 * 原先这里恒传一个 {@code ${PAY_CENTER_CALLBACK_URL:...}}，而 {@code BizDataBuilder} 是「入参优先、自身值兜底」
 * —— 于是闸机出站扣费这条主链路下发给支付中心的**永远是本模块那个值**，把 alipay-pay-sign 自己那份
 * 完全遮住；而那个值指向 fep-alipay 的 {@code /api/payment/payNotify}，**该路径在 fep-alipay 上不存在**
 * （它的真实回调路径是 {@code /notify/payment/payNotify}），回调因此长期落到无 handler 的路径上。
 * <b>NEVER 加回 setNotifyUrl</b> —— 一加回来，回调地址就又分散成两个模块各一份、且本模块那份优先。
 */
@Component
public class AlipayTripPayRequestFactory {

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
        payRequest.setIndustryDetail(order.getIndustryDetail());
        return payRequest;
    }
}
