package com.chinasofti.huateng.facepay.api.page;

/**
 * 运营端人工退款的请求体。
 *
 * <p><b>故意不含退款金额</b>：金额只从订单总额算，页面输入的金额一律不信任——
 * 这条约束沿用旧实现，防止运营端误填造成超额退款。</p>
 *
 * <p>放在 {@code api.page} 而不是 {@code controller.page}：本模块的入向契约一律归
 * {@code api/<渠道>}（{@code api/device/{tvm,bom,app}}、{@code api/paycenter}），
 * 运营后台是第四个渠道；{@code controller} 包只放 {@code @RestController}。</p>
 */
public class FacePayRefundRequest {

    /** 退款原因，为空时落 {@code 运营人工退款}。 */
    private String refundReason;

    /** 操作员，落到 {@code F2F_REFUND.OPERATOR_ID} 供审计追溯。旧实现没有这个字段。 */
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
