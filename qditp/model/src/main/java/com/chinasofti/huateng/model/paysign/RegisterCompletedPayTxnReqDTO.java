package com.chinasofti.huateng.model.paysign;

/**
 * 登记一条「已完成、但不经支付中心」的支付流水（内部契约，NEVER 暴露给 APP / 设备）。
 *
 * <p><b>为什么要有这个 DTO：</b>BOM 补站（{@code adviceOpt} 005 / 006 / 020）的钱由 BOM 现场收取，
 * ITP 侧不发起免密扣款（ADR-D136 + 用户 2026-09-22 裁决），因此这类订单走不到 {@code requestPay}，
 * {@code PAY_TXN_DETAIL} 里一条流水都没有。而用户 2026-09-22 裁决「没有流水行就不是完整订单」，
 * 故新增本契约，由 gate-txn-pay 在落单前调 {@code /internal/payment/registerCompletedTxn} 补一行。
 *
 * <p><b>NEVER 复用 {@code RequestPayReqDTO}</b>：那个是能被 {@code parseBizData} 解析的对外契约
 * （见 AGENTS.md §9 那条判据），而且它带 {@code scene} / {@code subject} / {@code body} 等
 * 只对支付中心有意义的字段，对「现场已收款」的单子没有语义。
 *
 * <p><b>金额口径 NEVER 改</b>：{@code amount} 恒为 ITP 实收金额，BOM 代收单即 {@code 0}。
 * 退款侧靠「{@code AMOUNT <= 0} 一律拒」把这类单挡在 {@code requestRefund} 之外
 * （{@code PayRefundRules.validateRefundPayTxn}），把 BOM 现场收的钱填进来等于把那道闸拆了。
 */
public class RegisterCompletedPayTxnReqDTO {

    /** 我方订单号，等于 {@code GATE_TXN_PAY.ORDER_NO}，也是 {@code UK_PAY_TXN_DETAIL_ORDER} 的幂等键。 */
    private String orderNo;

    /** 交易日期 {@code yyyyMMdd}，与订单同源；分区列 + 唯一键第二列，NEVER 留空。 */
    private String txnDate;

    private String thirdUserId;

    private String cardId;

    private String cardType;

    /** 真实签约渠道，照抄订单上的值；这一列表达「该用户签的是谁」，与「本笔谁收钱」无关。 */
    private String paymentVendor;

    private String requestSignSeq;

    private String payUserId;

    /** ITP 实收金额（单位分）。BOM 现场代收的单子传 {@code 0}。 */
    private Integer amount;

    /** 落库原因，进 {@code PAY_TXN_DETAIL} 无对应列，仅用于服务端日志留痕。 */
    private String reason;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getTxnDate() {
        return txnDate;
    }

    public void setTxnDate(String txnDate) {
        this.txnDate = txnDate;
    }

    public String getThirdUserId() {
        return thirdUserId;
    }

    public void setThirdUserId(String thirdUserId) {
        this.thirdUserId = thirdUserId;
    }

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    public String getCardType() {
        return cardType;
    }

    public void setCardType(String cardType) {
        this.cardType = cardType;
    }

    public String getPaymentVendor() {
        return paymentVendor;
    }

    public void setPaymentVendor(String paymentVendor) {
        this.paymentVendor = paymentVendor;
    }

    public String getRequestSignSeq() {
        return requestSignSeq;
    }

    public void setRequestSignSeq(String requestSignSeq) {
        this.requestSignSeq = requestSignSeq;
    }

    public String getPayUserId() {
        return payUserId;
    }

    public void setPayUserId(String payUserId) {
        this.payUserId = payUserId;
    }

    public Integer getAmount() {
        return amount;
    }

    public void setAmount(Integer amount) {
        this.amount = amount;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    @Override
    public String toString() {
        return "RegisterCompletedPayTxnReqDTO{orderNo='" + orderNo + "', txnDate='" + txnDate
                + "', thirdUserId='" + thirdUserId + "', cardId='" + cardId + "', cardType='" + cardType
                + "', paymentVendor='" + paymentVendor + "', amount=" + amount + ", reason='" + reason + "'}";
    }
}
