package com.chinasofti.huateng.dailyticket.page;

/** 运营端旅游票子单部分退款请求。 */
public class TravelTicketSubRefundRequest {
    private String parentOrderNo;
    private String subOrderNo;
    private String refundReason;
    private String operator;

    public String getParentOrderNo() { return parentOrderNo; }
    public void setParentOrderNo(String parentOrderNo) { this.parentOrderNo = parentOrderNo; }
    public String getSubOrderNo() { return subOrderNo; }
    public void setSubOrderNo(String subOrderNo) { this.subOrderNo = subOrderNo; }
    public String getRefundReason() { return refundReason; }
    public void setRefundReason(String refundReason) { this.refundReason = refundReason; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
}
