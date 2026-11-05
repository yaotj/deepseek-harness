package com.chinasofti.huateng.facepay.api.device.tvm;

import com.chinasofti.huateng.facepay.api.device.BaseDeviceRequest;

public class RequestRefundReqDTO extends BaseDeviceRequest {

    private String orderNo;

    private String refundReason;

    private String refundAmt;

    public Long amountInFen() {
        if (refundAmt == null || refundAmt.isBlank()) {
            return null;
        }
        try {
            long amount = Long.parseLong(refundAmt.trim());
            return amount > 0 ? amount : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getRefundReason() {
        return refundReason;
    }

    public void setRefundReason(String refundReason) {
        this.refundReason = refundReason;
    }

    public String getRefundAmt() {
        return refundAmt;
    }

    public void setRefundAmt(String refundAmt) {
        this.refundAmt = refundAmt;
    }

    @Override
    public String toString() {
        return super.toString() + ",RequestRefundReqDTO{orderNo=" + orderNo
                + ", refundAmt=" + refundAmt
                + ", refundReason=" + refundReason + '}';
    }
}
