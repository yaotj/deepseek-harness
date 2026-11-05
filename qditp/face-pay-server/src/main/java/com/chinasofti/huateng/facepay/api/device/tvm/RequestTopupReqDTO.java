package com.chinasofti.huateng.facepay.api.device.tvm;

import com.chinasofti.huateng.facepay.api.device.BaseDeviceRequest;

public class RequestTopupReqDTO extends BaseDeviceRequest {

    private String ticketLogicNum;

    private String ticketPhysicsNum;

    private String beforeAmount;

    private String transAmount;

    private String payType;

    public Long transAmountInFen() {
        return parse(transAmount);
    }

    public Long beforeAmountInFen() {
        Long parsed = parse(beforeAmount);
        return parsed != null && parsed >= 0 ? parsed : null;
    }

    private static Long parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public String getTicketLogicNum() {
        return ticketLogicNum;
    }

    public void setTicketLogicNum(String ticketLogicNum) {
        this.ticketLogicNum = ticketLogicNum;
    }

    public String getTicketPhysicsNum() {
        return ticketPhysicsNum;
    }

    public void setTicketPhysicsNum(String ticketPhysicsNum) {
        this.ticketPhysicsNum = ticketPhysicsNum;
    }

    public String getBeforeAmount() {
        return beforeAmount;
    }

    public void setBeforeAmount(String beforeAmount) {
        this.beforeAmount = beforeAmount;
    }

    public String getTransAmount() {
        return transAmount;
    }

    public void setTransAmount(String transAmount) {
        this.transAmount = transAmount;
    }

    public String getPayType() {
        return payType;
    }

    public void setPayType(String payType) {
        this.payType = payType;
    }

    @Override
    public String toString() {
        return super.toString() + ",RequestTopupReqDTO{ticketLogicNum=" + ticketLogicNum
                + ", ticketPhysicsNum=" + ticketPhysicsNum
                + ", beforeAmount=" + beforeAmount
                + ", transAmount=" + transAmount
                + ", payType=" + payType + '}';
    }
}
