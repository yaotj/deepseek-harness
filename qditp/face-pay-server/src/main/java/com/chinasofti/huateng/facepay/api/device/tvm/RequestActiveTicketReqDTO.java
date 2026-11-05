package com.chinasofti.huateng.facepay.api.device.tvm;

import com.chinasofti.huateng.facepay.api.device.BaseDeviceRequest;

public class RequestActiveTicketReqDTO extends BaseDeviceRequest {

    private String orderNo;

    private String qrcodeGenDate;

    private String randomFact;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getQrcodeGenDate() {
        return qrcodeGenDate;
    }

    public void setQrcodeGenDate(String qrcodeGenDate) {
        this.qrcodeGenDate = qrcodeGenDate;
    }

    public String getRandomFact() {
        return randomFact;
    }

    public void setRandomFact(String randomFact) {
        this.randomFact = randomFact;
    }

    @Override
    public String toString() {
        return super.toString() + ",RequestActiveTicketReqDTO{orderNo=" + orderNo
                + ", qrcodeGenDate=" + qrcodeGenDate
                + ", randomFact=" + randomFact + '}';
    }
}
