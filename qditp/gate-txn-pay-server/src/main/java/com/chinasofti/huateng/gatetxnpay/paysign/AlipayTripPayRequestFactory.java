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

/**
 * 支付宝出行免密扣费报文的唯一组装处，含 {@code alipay.trip.*} 六个配置项。
 *
 * <p><b>{@code payTriggerType}（支付触发方式：01-首次支付，02-补偿支付，V1.2 文档必填）由调用方
 * 按发起入口显式传入</b>：industryDetail 快照是出站时组好的、补偿重推复用同一份，只有发起方知道
 * 本次是首次出账还是补偿重推，NEVER 在 ticket-server 组装时写死。注入失败（快照为空 / 非法 JSON）
 * 只 WARN、原样透传 —— 缺一个对端字段的危害远小于扣款不发。
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

    private static final Logger log = LoggerFactory.getLogger(AlipayTripPayRequestFactory.class);

    /** 支付触发方式：首次支付（V1.2 文档 01）。出站扣款与离线码 fare 恢复都是渠道第一次见到这笔订单。 */
    public static final String PAY_TRIGGER_FIRST = "01";
    /** 支付触发方式：补偿支付（V1.2 文档 02）。重试队列 / RETRY 扫表 / 运营手动重试等重推入口。 */
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

    /**
     * 组装支付宝出行免密扣费报文，并把 {@code payTriggerType} 注入 industryDetail。
     *
     * @param payTriggerType {@link #PAY_TRIGGER_FIRST} 或 {@link #PAY_TRIGGER_COMPENSATION}，调用方按发起入口传
     */
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

    /**
     * 把 {@code payTriggerType} 写进 industryDetail JSON（V1.2 文档必填键）。
     * 快照为空或非法 JSON 时原样透传 + WARN —— 不阻断扣款（资损方向）。
     */
    private String applyPayTriggerType(String industryDetail, String payTriggerType, String orderNo) {
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
