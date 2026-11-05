package com.chinasofti.huateng.facepay.api.device.bom;

import com.chinasofti.huateng.facepay.api.device.BaseDeviceRequest;

public class RequestTicketRefundReqDTO extends BaseDeviceRequest {

    private String orderNo;

    private String ticketLogicNum;

    private String transAmount;

    private String transType;

    public Long amountInFen() {
        if (transAmount == null || transAmount.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(transAmount.trim());
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

    public String getTicketLogicNum() {
        return ticketLogicNum;
    }

    public void setTicketLogicNum(String ticketLogicNum) {
        this.ticketLogicNum = ticketLogicNum;
    }

    public String getTransAmount() {
        return transAmount;
    }

    public void setTransAmount(String transAmount) {
        this.transAmount = transAmount;
    }

    public String getTransType() {
        return transType;
    }

    public void setTransType(String transType) {
        this.transType = transType;
    }

    @Override
    public String toString() {
        return "RequestTicketRefundReqDTO{orderNo=" + orderNo
                + ", ticketLogicNum=" + ticketLogicNum
                + ", transAmount=" + transAmount
                + ", transType=" + transType
                + ", deviceId=" + getDeviceId() + '}';
    }
}
