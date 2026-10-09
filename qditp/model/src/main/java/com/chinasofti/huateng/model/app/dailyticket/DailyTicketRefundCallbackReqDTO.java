package com.chinasofti.huateng.model.app.dailyticket;

/**
 * 支付中心退款结果回调参数（网关接口文档 §3.3 退款回调，7 个字段）。
 */
public class DailyTicketRefundCallbackReqDTO {
    /**
     * 支付订单号（支付中心侧的平台单号），对应我方 {@code PAYMENT_ORDER_NO} / {@code TRADE_NO}。
     *
     * <p>NEVER 把它当成商户订单号去查 {@code DAILY_TICKET_ORDER.ORDER_NO} —— 2026-09-20 实测：
     * 该字段回显的是我方请求里送出的 {@code orderNo}（即 {@code PAYMENT_ORDER_NO}），
     * 拿它查订单表与退款单表一律 0 行。定位退款单 MUST 用 {@code outRefundNo}。
     * 它的用途只有一个：与退款单所属订单的 {@code PAYMENT_ORDER_NO} 比对，
     * 不一致即判为「另一次支付（重复支付）的退款」，不更新本业务单。
     */
    private String orderNo;

    /**
     * 商户订单号，即我方日票 / 旅游票订单号。
     *
     * <p>支付中心退款回调契约里有这个字段，但 2026-09-20 连续三笔实测**均未实际下发**，
     * 因此只能当「送了就用、没送就跳过」的可选校验项，NEVER 设为必填。
     */
    private String merchantOrderNo;

    /**
     * 退款结果：SUCCESS / FAIL / PROCESSING。
     */
    private String refundResult;

    /**
     * 退款结果描述。
     */
    private String refundResultDesc;

    /**
     * 退款完成时间，格式 yyyyMMddHHmmss。
     */
    private String refundDate;

    /**
     * 退款金额，单位分，字符串形态。
     */
    private String refundAmount;

    /**
     * 支付平台退款单号，回填 {@code DAILY_TICKET_REFUND.PLATFORM_REFUND_NO}。
     */
    private String refundNo;

    /**
     * 商户退款单号，对应 {@code DAILY_TICKET_REFUND.REFUND_ORDER_NO}。
     */
    private String outRefundNo;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getMerchantOrderNo() {
        return merchantOrderNo;
    }

    public void setMerchantOrderNo(String merchantOrderNo) {
        this.merchantOrderNo = merchantOrderNo;
    }

    public String getRefundResult() {
        return refundResult;
    }

    public void setRefundResult(String refundResult) {
        this.refundResult = refundResult;
    }

    public String getRefundResultDesc() {
        return refundResultDesc;
    }

    public void setRefundResultDesc(String refundResultDesc) {
        this.refundResultDesc = refundResultDesc;
    }

    public String getRefundDate() {
        return refundDate;
    }

    public void setRefundDate(String refundDate) {
        this.refundDate = refundDate;
    }

    public String getRefundAmount() {
        return refundAmount;
    }

    public void setRefundAmount(String refundAmount) {
        this.refundAmount = refundAmount;
    }

    public String getRefundNo() {
        return refundNo;
    }

    public void setRefundNo(String refundNo) {
        this.refundNo = refundNo;
    }

    public String getOutRefundNo() {
        return outRefundNo;
    }

    public void setOutRefundNo(String outRefundNo) {
        this.outRefundNo = outRefundNo;
    }

    @Override
    public String toString() {
        return "DailyTicketRefundCallbackReqDTO{orderNo='" + orderNo + "', merchantOrderNo='" + merchantOrderNo
                + "', refundResult='" + refundResult
                + "', refundResultDesc='" + refundResultDesc + "', refundDate='" + refundDate
                + "', refundAmount='" + refundAmount + "', refundNo='" + refundNo
                + "', outRefundNo='" + outRefundNo + "'}";
    }
}
