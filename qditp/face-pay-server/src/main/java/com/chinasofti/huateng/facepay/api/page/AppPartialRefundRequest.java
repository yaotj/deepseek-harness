package com.chinasofti.huateng.facepay.api.page;

/** 运营端按指定金额退款的请求体，对齐旧模块 {@code /page/app/orders/{orderNo}/refund} 的 {@code AppPartialRefundRequest}（那个类同样只在业务模块内、不在 {@code model} 模块 —— 它是运营后台与本服务之间的内部契约，不经 {@code parseBizData}，不属于对外契约）。 */
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
