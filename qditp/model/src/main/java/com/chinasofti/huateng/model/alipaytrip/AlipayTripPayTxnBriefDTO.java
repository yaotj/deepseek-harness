package com.chinasofti.huateng.model.alipaytrip;

/**
 * {@code ALIPAY_PAY_TXN_DETAIL} 的「补齐支付侧字段」批量查询出参 —— 支付宝出行乘车记录列表的**第二次请求**。
 *
 * <p>用途只有一个：乘车记录列表以 {@code GATE_TXN_PAY} 为主表分页（第一次请求）后，按本页 {@code orderNo}
 * 一次性把支付侧那几个字段取回来合并。**NEVER 把它当通用支付明细 DTO 用** —— 它刻意只有 5 个字段，
 * 加字段等于把这条内部链路变成第二个「支付明细查询」接口。
 *
 * <p><b>字段是库里的原值、不是契约值</b>：{@code payStatus} 是 {@code PAY_STATUS} 原文
 * （{@code INIT} / {@code PROCESSING} / {@code SUCCESS} / {@code FAIL} ...），
 * 契约要的 {@code debitRequestResult}（{@code 0} / {@code 1}）由调用方映射；
 * {@code transTime} 是回调报文原文、格式不统一（既有 {@code 2026-09-18 16:44:41} 也有
 * {@code 20260918021500}），**NEVER 在这条链路上解析或格式化它**。
 */
public class AlipayTripPayTxnBriefDTO {

    /** 扣费订单号，与 {@code GATE_TXN_PAY.ORDER_NO} 同值，合并时的关联键。 */
    private String orderNo;

    /** {@code PAY_STATUS} 原文，契约的 {@code debitRequestResult} 由调用方按它映射。 */
    private String payStatus;

    /** {@code CHANNEL_ORDER_NO}，对应契约的 {@code payTradeOrderNo}（支付宝渠道流水号）。 */
    private String channelOrderNo;

    /** {@code TRANS_TIME}，对应契约的 {@code payOrderNoDate}。原文直传，NEVER 解析。 */
    private String transTime;

    /** {@code INVOICE}。按 2026-09-18 的裁决**只原样返回、不参与列表筛选**。 */
    private String invoice;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getPayStatus() {
        return payStatus;
    }

    public void setPayStatus(String payStatus) {
        this.payStatus = payStatus;
    }

    public String getChannelOrderNo() {
        return channelOrderNo;
    }

    public void setChannelOrderNo(String channelOrderNo) {
        this.channelOrderNo = channelOrderNo;
    }

    public String getTransTime() {
        return transTime;
    }

    public void setTransTime(String transTime) {
        this.transTime = transTime;
    }

    public String getInvoice() {
        return invoice;
    }

    public void setInvoice(String invoice) {
        this.invoice = invoice;
    }

    @Override
    public String toString() {
        return "AlipayTripPayTxnBriefDTO{orderNo='" + orderNo + "', payStatus='" + payStatus
                + "', channelOrderNo='" + channelOrderNo + "', transTime='" + transTime
                + "', invoice='" + invoice + "'}";
    }
}
