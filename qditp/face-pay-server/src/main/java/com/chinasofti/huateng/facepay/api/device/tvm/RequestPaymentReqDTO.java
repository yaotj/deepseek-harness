package com.chinasofti.huateng.facepay.api.device.tvm;

import com.chinasofti.huateng.facepay.api.device.BaseDeviceRequest;

public class RequestPaymentReqDTO extends BaseDeviceRequest {

    private String orderNo;

    private String paymentCode;

    private String paymentVendor;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getPaymentCode() {
        return paymentCode;
    }

    public void setPaymentCode(String paymentCode) {
        this.paymentCode = paymentCode;
    }

    public String getPaymentVendor() {
        return paymentVendor;
    }

    public void setPaymentVendor(String paymentVendor) {
        this.paymentVendor = paymentVendor;
    }

    @Override
    public String toString() {
        return super.toString() + ",RequestPaymentReqDTO{orderNo=" + orderNo
                + ", paymentCode=***"
                + ", paymentVendor=" + paymentVendor + '}';
    }
}
