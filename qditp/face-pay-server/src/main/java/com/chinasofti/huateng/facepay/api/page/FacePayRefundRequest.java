package com.chinasofti.huateng.facepay.api.page;

/** 运营端人工退款的请求体。 */
public class FacePayRefundRequest {

    /** 退款原因，为空时落 {@code 运营人工退款}。 */
    private String refundReason;

    /** 操作员，落到 {@code F2F_REFUND.OPERATOR_ID} 供审计追溯。 */
    private String operatorId;

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
