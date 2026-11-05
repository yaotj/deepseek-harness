package com.chinasofti.huateng.facepay.api.device.tvm;

import com.chinasofti.huateng.facepay.api.device.BaseDeviceRequest;

public class TopupCardResultNotiReqDTO extends BaseDeviceRequest {

    private String orderNo;

    private String ticketLogicNum;

    private String ticketPhysicsNum;

    private String transDate;

    private String transAmount;

    private String afterAmount;

    public Long transAmountInFen() {
        return parse(transAmount);
    }

    public Long afterAmountInFen() {
        return parse(afterAmount);
    }

    private static Long parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
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

    public String getTicketPhysicsNum() {
        return ticketPhysicsNum;
    }

    public void setTicketPhysicsNum(String ticketPhysicsNum) {
        this.ticketPhysicsNum = ticketPhysicsNum;
    }

    public String getTransDate() {
        return transDate;
    }

    public void setTransDate(String transDate) {
        this.transDate = transDate;
    }

    public String getTransAmount() {
        return transAmount;
    }

    public void setTransAmount(String transAmount) {
        this.transAmount = transAmount;
    }

    public String getAfterAmount() {
        return afterAmount;
    }

    public void setAfterAmount(String afterAmount) {
        this.afterAmount = afterAmount;
    }

    @Override
    public String toString() {
        return super.toString() + ",TopupCardResultNotiReqDTO{orderNo=" + orderNo
                + ", ticketLogicNum=" + ticketLogicNum
                + ", transAmount=" + transAmount
                + ", afterAmount=" + afterAmount + '}';
    }
}
