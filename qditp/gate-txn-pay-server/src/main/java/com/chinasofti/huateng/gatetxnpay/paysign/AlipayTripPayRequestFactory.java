package com.chinasofti.huateng.gatetxnpay.paysign;

import com.chinasofti.huateng.gatetxnpay.entity.GateTxnPay;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestPayReqDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 支付宝出行免密扣费报文的**唯一组装处**，含 {@code alipay.trip.*} 六个配置项与回调地址。
 *
 * <p>与 {@link GatePayRequestFactory} 分成两个类而不是一个带分支的类，是因为
 * <b>两套配置的单位与语义不同、只是名字像</b>：本类的 {@code orderTimeOut} 是**分钟**
 * （pay-sign 那边是秒）、{@code requestSignSeq} 取 {@code TICKET_TRANS_SEQ}
 * （支付宝按票卡流水号找协议）、{@code notifyUrl} 必须显式带上（支付宝的扣费结果只回调到这个地址）。
 * <b>NEVER 把两套配置合并成一组</b>。</p>
 *
 * <p>报文与合并前 fep-dev-server 的 {@code requestAlipayTripPay} 完全一致，只有两处换了来源：
 * 订单号改用 {@code GATE_TXN_PAY.ORDER_NO}（合并的目的就是一单一号），
 * {@code industryDetail} 改用落单时透传存下的 {@code INDUSTRY_DETAIL}。</p>
 *
 * <p>{@code industryDetail} <b>NEVER 在这里重算</b>：那 21 键里有 9 个（进出站线路码 / 名称、
 * 进站设备号、entryId / exitId、cardNum、cardIssueCode）在 {@code GATE_TXN_PAY} 没有列，
 * 只有出站那一刻 fep-dev-server 的三个并行 RPC 拿得到；用订单快照顶替会得到一份键名
 * 完全不同的 JSON，支付宝侧解析不出行程、扣费直接失败。</p>
 */
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

    /** 组装支付宝出行免密扣费报文。无入向 request 参数：三条链路都只能取订单快照。 */
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
