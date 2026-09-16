package com.chinasofti.huateng.facepay.api.page;

/**
 * 运营端<b>按指定金额</b>退款的请求体，对齐旧模块 {@code /page/app/orders/{orderNo}/refund}
 * 的 {@code AppPartialRefundRequest}（那个类同样只在业务模块内、不在 {@code model} 模块 ——
 * 它是运营后台与本服务之间的内部契约，不经 {@code parseBizData}，不属于对外契约）。
 *
 * <p>与旧类的差异：旧类只有 {@code refundAmount} 一个字段，本类补 {@code refundReason} 与
 * {@code operatorId}，与本模块 {@link FacePayRefundRequest} 看齐 —— 退款单要落
 * {@code F2F_REFUND.OPERATOR_ID} 才能查「谁点的」，旧实现没有这个列可落。
 *
 * <p><b>{@code refundAmount} 单位是分</b>，与 {@code F2F_ORDER.ORDER_AMOUNT} 同单位，
 * NEVER 改成元 —— 全链路（设备报文、支付中心、`F2F_*` 三张表）都是分。
 */
public class AppPartialRefundRequest {

    /** 本次退款金额，单位分，MUST 大于 0 且不大于剩余可退金额。 */
    private Long refundAmount;

    /** 退款原因，可空，落 {@code F2F_REFUND.REFUND_REASON}。 */
    private String refundReason;

    /** 操作员标识，可空，落 {@code F2F_REFUND.OPERATOR_ID}。 */
    private String operatorId;

    public Long getRefundAmount() {
        return refundAmount;
    }

    public void setRefundAmount(Long refundAmount) {
        this.refundAmount = refundAmount;
    }

    public String getRefundReason() {
        return refundReason;
    }

    public void setRefundReason(String refundReason) {
        this.refundReason = refundReason;
    }

    public String getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(String operatorId) {
        this.operatorId = operatorId;
    }
}
