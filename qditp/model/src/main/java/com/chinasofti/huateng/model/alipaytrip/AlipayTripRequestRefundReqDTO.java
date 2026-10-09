package com.chinasofti.huateng.model.alipaytrip;

/**
 * 支付宝出行-退款申请请求参数。
 *
 * <p><b>本 DTO 只有两个字段，且这是实测过的真实契约</b>（2026-09-20 修正）：入口是
 * {@code fep-alipay} 的 {@code POST /admin/payment/requestRefund}（`parseBizData` 解析成本类）→
 * {@code rpc/AlipayPaySignClient.alipayTripRequestRefund} → {@code alipay-pay-sign-server} 的
 * {@code POST /internal/alipay/payment/requestRefund}。
 *
 * <p><b>本类此前多出 `cardIssueCode` / `cardNum` / `channelAgreementNo` / `refundOrderNo` 四个字段，
 * 已删除、NEVER 加回</b>：接收端 {@code alipay-pay-sign-server} 的同名 DTO
 * （{@code ...alipay.paysign.model.request.AlipayTripRequestRefundReqDTO}）本来就只有这两个字段，
 * Fastjson2 宽松模式**静默丢弃**目标类里不存在的字段，因此那四个值**从来没有到达过服务端**，
 * 调用方按 6 字段传值是无效的（既不报错也不告警，属 AGENTS.md §7「同名 DTO 字段数不一致」那类静默缺陷）。
 *
 * <p><b>那四个值也不该由调用方给</b>，服务端是刻意自己解析的（`AlipayPayRefundServiceImpl`）：
 * {@code cardNum} 取原支付单 {@code ALIPAY_PAY_LOG.CARD_ID}、{@code channelAgreementNo} 查
 * {@code ALIPAY_SIGN_INFO} 的生效签约、{@code cardIssueCode} 是支付宝渠道常量 {@code 0007}、
 * {@code refundOrderNo} 由服务端生成（`R` + 毫秒 + 8 位 UUID）并受唯一索引
 * {@code UK_ARL_REFUND_ORDER_NO} 约束。<b>让调用方指定退款单号或渠道协议号等于把资金键交给外部，
 * NEVER 改成「信调用方送的值」。</b>
 *
 * <p>{@code refundAmount} 单位分，不传即按原支付金额全额退（`RefundAmountCalculator`）。
 */
public class AlipayTripRequestRefundReqDTO {

    /**
     * 原订单号。
     */
    private String orderNo;

    /**
     * 退款金额，单位分；不传则默认全额退款。
     */
    private String refundAmount;

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
}
