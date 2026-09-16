package com.chinasofti.huateng.gatetxnpay.paysign;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.gatetxnpay.entity.GateTxnPay;
import com.chinasofti.huateng.model.app.GatePayRequestDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayReqDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * pay-sign（支付中心）免密扣款报文的**唯一组装处**，含 {@code gate.pay.*} 五个配置项。
 *
 * <p>从 {@link PaySignInitiator} 拆出来的理由：那个类原本一个构造器要收 11 个 {@code @Value}，
 * 两个渠道的配置混在同一串参数里，<b>而两套配置的单位与语义并不相同</b>
 * （本类的 {@code orderTimeOut} 是**秒**，支付宝那套是**分钟**）。参数一多，
 * 「哪个配置属于哪个渠道」就只能靠命名前缀记 —— 写错一个不报错，只在对端超时行为上表现出来。</p>
 *
 * <p>本类<b>只组报文、不发 RPC、不碰 mapper</b>。加进任何 client 或 mapper 都会让
 * 「出账口只有一处」这条约束失效。</p>
 */
@Component
public class GatePayRequestFactory {

    private final String payScene;
    private final String industryType;
    private final String subject;
    private final String body;
    private final Long orderTimeoutSeconds;

    public GatePayRequestFactory(
            @Value("${gate.pay.scene:AGM_GATE}") String payScene,
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
     * <p>这里不决定签约渠道和签约流水号，pay-sign 会根据 thirdUserId/cardId/cardType
     * 去 account-server 查询 USER_ITP_REG_INFO 中的默认支付通道和 REQ_CONTRACT_NO。</p>
     *
     * @param request 入向报文，运营重试与离线码补偿两条链路为 {@code null}，此时支付相关
     *                字段只能取订单快照
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
        // TXN_DATE MUST 取行程侧订单的交易日期（出站日），NEVER 让支付域自己取当日：
        // 两表按 (ORDER_NO, TXN_DATE) 关联且都以它做月分区，出站到落库最大滞后实测 94 分钟，
        // 22:26 之后出站时支付域取 now() 会跨日、关联即落空。补单重试走同一条路，值仍取订单快照。
        payRequest.setTxnDate(order.getTxnDate());
        // 支付相关字段仅用于 pay-sign，不持久化到 GATE_TXN_PAY；重试时可能为 null
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

    /**
     * 行业明细先存完整订单快照，便于支付中心侧排查交易来源。
     *
     * <p>注意这与支付宝出行那条链路的 {@code industryDetail} <b>不是一回事</b>：
     * 那边是甲方规定的 21 键 JSON、只能取落单时透传存下的原值，
     * <b>NEVER 拿本方法的订单快照顶替</b>（键名完全不同，支付宝侧解析不出行程）。</p>
     */
    private String buildIndustryDetail(GateTxnPay order) {
        return JSON.toJSONString(order);
    }
}
