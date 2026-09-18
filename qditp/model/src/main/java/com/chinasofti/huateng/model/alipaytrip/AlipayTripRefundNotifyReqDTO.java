package com.chinasofti.huateng.model.alipaytrip;

/**
 * 支付宝出行-退款结果回调请求参数（支付中心网关契约 §5.2）。
 */
public class AlipayTripRefundNotifyReqDTO {

    /**
     * 商户订单号（原支付订单号）。
     */
    private String orderNo;

    /**
     * 退款结果 SUCCESS-成功 FAIL-失败 PROCESSING-处理中。
     */
    private String refundResult;

    /**
     * 退款结果描述。
     */
    private String refundResultDesc;

    /**
     * 退款时间 yyyy-MM-dd HH:mm:ss。
     */
    private String refundDate;

    /**
     * 退款金额，单位分。
     */
    private String refundAmount;

    /**
     * 支付中心退款流水号。
     */
    private String refundNo;

    /**
     * 商户退款流水号。
     */
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
}
