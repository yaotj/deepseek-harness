package com.chinasofti.huateng.model.app;

/**
 * 支付中心网关 §5.2 退款回调业务参数（2026-09-22 新增，P1-3）。
 *
 * <p>字段逐条取自 {@code docs/external/支付中心网关接口文档.md} §5.2，**这是对外契约、NEVER 加字段**。
 *
 * <p>两个退款单号 MUST 分清，搞反就定位不到本地行：
 * <ul>
 *   <li>{@code outRefundNo} —— **我方**的退款流水号，等于 {@code PAY_REFUND_DETAIL.REFUND_ORDER_NO}，
 *       也就是 §3.1 请求退款时我方送出的 {@code refundOrderNo}。定位本地行只能靠它。</li>
 *   <li>{@code refundNo} —— **支付中心侧**生成的退款流水号，只作落库留痕。</li>
 * </ul>
 *
 * <p>{@code orderNo} 是支付中心侧的原支付订单号（等于 {@code PAY_TXN_DETAIL.PAY_CENTER_ORDER_NO}），
 * **不是**我方的 {@code ORDER_NO}，NEVER 拿它去查 {@code PAY_TXN_DETAIL.ORDER_NO}。
 *
 * <p>回调报文里**没有 {@code txnDate}**，而 {@code PAY_REFUND_DETAIL} 的键是
 * {@code REFUND_ORDER_NO + TXN_DATE} —— 因此收口前 MUST 先按 {@code outRefundNo} 回查拿到 {@code TXN_DATE}。
 */
public class ReceiveRefundResultReqDTO {
    /** 支付中心侧原支付订单号。 */
    private String orderNo;
    /** 退款结果：{@code SUCCESS} / {@code FAIL} / {@code PROCESSING}。 */
    private String refundResult;
    /** 退款结果描述。 */
    private String refundResultDesc;
    /** 退款时间。 */
    private String refundDate;
    /** 退款金额（单位分）。 */
    private Integer refundAmount;
    /** 支付中心侧退款流水号。 */
    private String refundNo;
    /** 我方退款流水号（{@code REFUND_ORDER_NO}），定位本地行的唯一键。 */
    private String outRefundNo;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
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

    public Integer getRefundAmount() {
        return refundAmount;
    }

    public void setRefundAmount(Integer refundAmount) {
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
        return "ReceiveRefundResultReqDTO{" +
                "orderNo='" + orderNo + '\'' +
                ", refundResult='" + refundResult + '\'' +
                ", refundResultDesc='" + refundResultDesc + '\'' +
                ", refundDate='" + refundDate + '\'' +
                ", refundAmount=" + refundAmount +
                ", refundNo='" + refundNo + '\'' +
                ", outRefundNo='" + outRefundNo + '\'' +
                '}';
    }
}
