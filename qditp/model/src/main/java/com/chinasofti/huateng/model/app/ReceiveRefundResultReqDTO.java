package com.chinasofti.huateng.model.app;


public class ReceiveRefundResultReqDTO {
    private String orderNo;
    private String refundResult;
    private String refundResultDesc;
    private String refundDate;
    private Integer refundAmount;
    private String refundNo;
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
