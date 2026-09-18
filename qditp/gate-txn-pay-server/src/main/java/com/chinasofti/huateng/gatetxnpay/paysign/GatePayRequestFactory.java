package com.chinasofti.huateng.gatetxnpay.paysign;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.gatetxnpay.entity.GateTxnPay;
import com.chinasofti.huateng.model.app.GatePayRequestDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayReqDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** pay-sign（支付中心）免密扣款报文的唯一组装处，含 {@code gate.pay.*} 五个配置项。 */
@Component
public class GatePayRequestFactory {

    /** 网关 §1.1 的 {@code scene} 枚举里免密后付费恒为 {@code withholding}，NEVER 退回非法的 {@code AGM_GATE}。 */
    private final String payScene;
    private final String industryType;
    private final String subject;
    private final String body;
    private final Long orderTimeoutSeconds;

    public GatePayRequestFactory(
            @Value("${gate.pay.scene:withholding}") String payScene,
            @Value("${gate.pay.industry-type:1}") String industryType,
            @Value("${gate.pay.subject:地铁乘车扣费}") String subject,
            @Value("${gate.pay.body:地铁乘车费用}") String body,
            @Value("${gate.pay.order-timeout-seconds:60}") Long orderTimeoutSeconds) {
        this.payScene = payScene;
        this.industryType = industryType;
        this.subject = subject;
        this.body = body;
        this.orderTimeoutSeconds = orderTimeoutSeconds;
    }

    /**
     * 组装免密扣款报文。
     *
     * @param request 入向报文，运营重试与离线码补偿两条链路为 {@code null}，此时支付相关。
     */
    public GatePayRequestDTO build(GateTxnPay order, GateTxnPayReqDTO request) {
        GatePayRequestDTO payRequest = new GatePayRequestDTO();
        payRequest.setOrderNo(order.getOrderNo());
        payRequest.setScene(payScene);
        payRequest.setAmount(order.getTotalAmount());
        payRequest.setIndustryType(industryType);
        payRequest.setSubject(subject);
        payRequest.setBody(body);
        payRequest.setThirdUserId(order.getThirdUserId());
        payRequest.setCardId(order.getCardId());
        payRequest.setCardType(order.getCardType());
        payRequest.setOrderTimeOut(orderTimeoutSeconds);
        payRequest.setIndustryDetail(buildIndustryDetail(order));
        payRequest.setPaymentVendor(order.getPaymentVendor());
        payRequest.setPayUserId(order.getPayUserId());
        payRequest.setTxnDate(order.getTxnDate());
        if (request != null) {
            if (StringUtils.hasText(request.getPaymentVendor())) {
                payRequest.setPaymentVendor(request.getPaymentVendor());
            }
            if (StringUtils.hasText(request.getPayUserId())) {
                payRequest.setPayUserId(request.getPayUserId());
            }
            payRequest.setRequestSignSeq(request.getRequestSignSeq());
            payRequest.setDiscountFee(request.getDiscountFee());
            payRequest.setDiscountInfo(request.getDiscountInfo());
        }
        return payRequest;
    }

    /** 行业明细先存完整订单快照，便于支付中心侧排查交易来源。 */
    private String buildIndustryDetail(GateTxnPay order) {
        return JSON.toJSONString(order);
    }
}
