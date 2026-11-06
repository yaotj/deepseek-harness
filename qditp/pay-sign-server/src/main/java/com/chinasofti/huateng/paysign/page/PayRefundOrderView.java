package com.chinasofti.huateng.paysign.page;

/**
 * 综管台扣费明细页的退款回填视图（按<b>原支付订单号</b>批量取）。
 *
 * <p>一行 = 一条退款明细；同一个 {@code orderNo} 可能有多行（2026-09-22 实测 7 个原单被重复提交 3~4 次），
 * 所以调用方 MUST 先按 {@link #orderNo} 分组再决定展示口径，NEVER 假定一单一行。
 *
 * <p><b>刻意不取 {@code REQUEST_BODY} / {@code RESPONSE_BODY} 两个 CLOB</b>：批量接口一次要回上百行，
 * 把支付中心原始报文一起拉回来会把结果集体积撑爆，而运营看的是状态与金额、不是报文原文。
 */
public class PayRefundOrderView {

    /** 原支付订单号，闸机后付费域等于 {@code GATE_TXN_PAY.ORDER_NO}。 */
    private String orderNo;

    /** 我方退款流水号（RF 前缀，全局唯一）。 */
    private String refundOrderNo;

    /**
     * 退款状态：INIT / PROCESSING / SUCCESS / FAIL / RETRY / CLOSED。
     *
     * <p>{@code RETRY} 是旧版本遗留（现已不再写入，库里 17 行）；
     * {@code CLOSED} 是回查超龄收口的「结果未知」态，**NEVER 当成「确认未退成功」**。
     */
    private String refundStatus;

    /** 本笔退款金额，单位分。 */
    private Long refundAmount;

    /** 退款到账时间；未收口时可能为 NULL，展示侧按「未回填」处理，NEVER 编原因。 */
    private String refundTime;

    /** 交易日期 yyyyMMdd（本表键的一部分）。 */
    private String txnDate;

    /** 已向支付中心发起的请求次数。 */
    private Integer requestCount;

    /** 支付中心返回码/报文，运营据它判断 CLOSED 那批到底卡在哪。 */
    private String payCenterCode;

    private String payCenterMsg;

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    public String getRefundOrderNo() { return refundOrderNo; }
    public void setRefundOrderNo(String refundOrderNo) { this.refundOrderNo = refundOrderNo; }

    public String getRefundStatus() { return refundStatus; }
    public void setRefundStatus(String refundStatus) { this.refundStatus = refundStatus; }

    public Long getRefundAmount() { return refundAmount; }
    public void setRefundAmount(Long refundAmount) { this.refundAmount = refundAmount; }

    public String getRefundTime() { return refundTime; }
    public void setRefundTime(String refundTime) { this.refundTime = refundTime; }

    public String getTxnDate() { return txnDate; }
    public void setTxnDate(String txnDate) { this.txnDate = txnDate; }

    public Integer getRequestCount() { return requestCount; }
    public void setRequestCount(Integer requestCount) { this.requestCount = requestCount; }

    public String getPayCenterCode() { return payCenterCode; }
    public void setPayCenterCode(String payCenterCode) { this.payCenterCode = payCenterCode; }

    public String getPayCenterMsg() { return payCenterMsg; }
    public void setPayCenterMsg(String payCenterMsg) { this.payCenterMsg = payCenterMsg; }
}
