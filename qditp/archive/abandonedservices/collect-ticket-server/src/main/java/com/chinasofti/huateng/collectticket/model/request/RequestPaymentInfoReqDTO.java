package com.chinasofti.huateng.collectticket.model.request;

public class RequestPaymentInfoReqDTO {
    private String orderNo;
    private String payChannelCode;
    private String channelType;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getPayChannelCode() {
        return payChannelCode;
    }

    public void setPayChannelCode(String payChannelCode) {
        this.payChannelCode = payChannelCode;
    }

    public String getChannelType() {
        return channelType;
    }

    public void setChannelType(String channelType) {
        this.channelType = channelType;
    }

    @Override
    public String toString() {
        return "RequestPaymentInfoReqDTO{" +
                "orderNo='" + orderNo + '\'' +
                ", payChannelCode='" + payChannelCode + '\'' +
                ", channelType='" + channelType + '\'' +
                '}';
    }
}
