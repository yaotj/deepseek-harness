package com.chinasofti.huateng.model.alipaytrip;

/**
 * 支付宝出行-退款申请应答（读新表那一条链路，配 {@link AlipayTripTxnRefundReqDTO}）。
 *
 * <p>比旧 {@link AlipayTripRequestRefundRespDTO} 多一个 {@code refundOrderNo}，这是**有意的**：
 * 新链路的退款单号受唯一索引 {@code UK_ARTD_REFUND_ORDER}（{@code REFUND_ORDER_NO + TXN_DATE}）约束，
 * 把它回给调用方后，运维在页面上就能直接拿它去 {@code ALIPAY_REFUND_TXN_DETAIL} 与支付中心两侧对账；
 * 旧链路不回这个值，出问题只能靠时间窗猜。<b>NEVER 因为「和旧应答对齐」把它删掉。</b>
 *
 * <p>{@code retCode} 取 {@code FepAppErrorCodeEnum}：{@code 0000} 成功、{@code 9999} 失败、
 * {@code 8999} 结果未知（拿不到支付中心业务应答，明细留 {@code PROCESSING} 等人工核对）。
 * <b>结果未知 NEVER 当成失败处置</b> —— 钱可能已经退出去了。
 */
public class AlipayTripTxnRefundRespDTO {

    private String retCode;

    private String retMsg;

    /** 我方退款单号；只有已落库（明细已生成）时才有值。 */
    private String refundOrderNo;

    public String getRetCode() {
        return retCode;
    }

    public void setRetCode(String retCode) {
        this.retCode = retCode;
    }

    public String getRetMsg() {
        return retMsg;
    }

    public void setRetMsg(String retMsg) {
        this.retMsg = retMsg;
    }

    public String getRefundOrderNo() {
        return refundOrderNo;
    }

    public void setRefundOrderNo(String refundOrderNo) {
        this.refundOrderNo = refundOrderNo;
    }
}
