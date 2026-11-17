package com.chinasofti.huateng.model.app.dailyticket;


public class DailyTicketRefundCallbackReqDTO {

    private String orderNo;


    private String merchantOrderNo;


    private String refundResult;


    private String refundResultDesc;


    private String refundDate;


    private String refundAmount;


    private String refundNo;


    private String outRefundNo;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getMerchantOrderNo() {
        return merchantOrderNo;
    }

    public void setMerchantOrderNo(String merchantOrderNo) {
        this.merchantOrderNo = merchantOrderNo;
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
        return "DailyTicketRefundCallbackReqDTO{orderNo='" + orderNo + "', merchantOrderNo='" + merchantOrderNo
                + "', refundResult='" + refundResult
                + "', refundResultDesc='" + refundResultDesc + "', refundDate='" + refundDate
                + "', refundAmount='" + refundAmount + "', refundNo='" + refundNo
                + "', outRefundNo='" + outRefundNo + "'}";
    }
}
