package com.chinasofti.huateng.model.alipaytrip;


public class AlipayTripPayNotifyReqDTO {


    private String orderNo;


    private String channelVoucherId;


    private String transAmount;


    private String transTime;


    private String transStatus;


    private String cardNo;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getChannelVoucherId() {
        return channelVoucherId;
    }

    public void setChannelVoucherId(String channelVoucherId) {
        this.channelVoucherId = channelVoucherId;
    }

    public String getTransAmount() {
        return transAmount;
    }

    public void setTransAmount(String transAmount) {
        this.transAmount = transAmount;
    }

    public String getTransTime() {
        return transTime;
    }

    public void setTransTime(String transTime) {
        this.transTime = transTime;
    }

    public String getTransStatus() {
        return transStatus;
    }

    public void setTransStatus(String transStatus) {
        this.transStatus = transStatus;
    }

    public String getCardNo() {
        return cardNo;
    }

    public void setCardNo(String cardNo) {
        this.cardNo = cardNo;
    }
}
