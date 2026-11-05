package com.chinasofti.huateng.facepay.api.device.app;

import com.chinasofti.huateng.facepay.api.paycenter.PayCenterCallbackRequest;

public class AppRefundNotiResultReqDTO {

    private String orderNo;

    private String refundNo;

    private String outRefundNo;

    private String refundResult;

    private String refundResultDesc;

    private String refundDate;

    private String refundAmount;

    public boolean isSuccess() {
        return "SUCCESS".equals(refundResult);
    }

    public boolean isFailed() {
        return "FAIL".equals(refundResult);
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
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

    @Override
    public String toString() {
        return "AppRefundNotiResultReqDTO{orderNo=" + orderNo
                + ", refundNo=" + refundNo
                + ", outRefundNo=" + outRefundNo
                + ", refundResult=" + refundResult
                + ", refundDate=" + refundDate
                + ", refundAmount=" + refundAmount + '}';
    }
}
