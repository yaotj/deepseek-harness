package com.chinasofti.huateng.model.app.dailyticket;

/**
 * 支付中心退款结果回调参数（网关接口文档 §3.3 退款回调，7 个字段）。
 */
public class DailyTicketRefundCallbackReqDTO {
    /**
     * 商户订单号，即日票订单号 {@code DAILY_TICKET_ORDER.ORDER_NO}。
     */
    private String orderNo;

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
        return "DailyTicketRefundCallbackReqDTO{orderNo='" + orderNo + "', refundResult='" + refundResult
                + "', refundResultDesc='" + refundResultDesc + "', refundDate='" + refundDate
                + "', refundAmount='" + refundAmount + "', refundNo='" + refundNo
                + "', outRefundNo='" + outRefundNo + "'}";
    }
}
