package com.chinasofti.huateng.model.alipaytrip;

/**
 * 支付宝出行-退款申请请求参数（**读新表 {@code ALIPAY_PAY_TXN_DETAIL} 的那一条链路**）。
 *
 * <p>入口是 {@code alipay-pay-sign-server} 的 {@code POST /internal/alipay/payment/requestTxnRefund}，
 * 唯一调用方是 {@code gate-txn-pay-server} 的运营后台退款分流
 * （{@code GateTxnPayManualOpsService.requestAlipayTripRefund}），走
 * {@code rpc/AlipayPaySignClient.alipayTripTxnRefund}。
 *
 * <p><b>为什么另起一个 DTO 而不是给 {@link AlipayTripRequestRefundReqDTO} 加字段</b>：那一个服务的是
 * 旧链路（读 {@code ALIPAY_PAY_LOG}、落 {@code ALIPAY_REFUND_LOG}），而 {@code ALIPAY_PAY_LOG}
 * 自「落单收口到 gate-txn-pay」之后**已无写入方**，按它退款必然报「原支付记录不存在」。两条链路的
 * 原支付来源、落库目标表、状态字面量都不同，<b>NEVER 合并这两个 DTO、也 NEVER 让新端点转发给旧实现</b>
 * —— 合并后「线上跑的是哪一侧」就无法从报文判断了。旧 DTO 与旧端点**原样保留作回滚位**。
 *
 * <p>三个字段的口径，<b>NEVER 加第四个</b>：
 * <ul>
 *   <li>{@code orderNo} —— 必填，退哪一笔的唯一依据；</li>
 *   <li>{@code refundAmount} —— 单位分，<b>刻意允许为空</b>，空即「按可退余额全额退」，
 *       由服务端用 {@code ALIPAY_PAY_TXN_DETAIL.AMOUNT} 减 {@code REFUND_AMOUNT} 算出；</li>
 *   <li>{@code refundReason} —— 运维在页面填的退款原因，只落 {@code ALIPAY_REFUND_TXN_DETAIL.REFUND_REASON}
 *       留痕，<b>NEVER 送进支付中心 bizData</b>（对端契约里没有这个键，送了会被静默丢）。</li>
 * </ul>
 *
 * <p><b>NEVER 加 {@code cardNum} / {@code channelAgreementNo} / {@code refundOrderNo} / {@code cardIssueCode}</b>：
 * 前两个由服务端自己解析（协议号取原支付明细的 {@code CHANNEL_AGREEMENT_NO}、卡号按协议号回查
 * {@code ALIPAY_SIGN_INFO}），{@code refundOrderNo} 由服务端生成并受唯一索引
 * {@code UK_ARTD_REFUND_ORDER} 约束，{@code cardIssueCode} 是支付宝渠道常量 {@code 0007}。
 * 让调用方指定这几个值等于把资金键交给外部。
 */
public class AlipayTripTxnRefundReqDTO {

    /** 原订单号（{@code GATE_TXN_PAY.ORDER_NO} = {@code ALIPAY_PAY_TXN_DETAIL.ORDER_NO}）。 */
    private String orderNo;

    /** 退款金额，单位分；不传即按可退余额全额退。 */
    private String refundAmount;

    /** 退款原因，只用于落库留痕。 */
    private String refundReason;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getRefundAmount() {
        return refundAmount;
    }

    public void setRefundAmount(String refundAmount) {
        this.refundAmount = refundAmount;
    }

    public String getRefundReason() {
        return refundReason;
    }

    public void setRefundReason(String refundReason) {
        this.refundReason = refundReason;
    }
}
