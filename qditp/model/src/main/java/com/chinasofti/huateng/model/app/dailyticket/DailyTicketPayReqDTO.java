package com.chinasofti.huateng.model.app.dailyticket;


public class DailyTicketPayReqDTO {

    private String thirdUserId;


    private String orderType;


    private String orderNo;


    private String payChannelCode;


    private String phone;


    private String channelType;


    private String cardId;

    public String getThirdUserId() {
        return thirdUserId;
    }

    public void setThirdUserId(String thirdUserId) {
        this.thirdUserId = thirdUserId;
    }

    public String getOrderType() {
        return orderType;
    }

    public void setOrderType(String orderType) {
        this.orderType = orderType;
    }

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

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getChannelType() {
        return channelType;
    }

    public void setChannelType(String channelType) {
        this.channelType = channelType;
    }

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    @Override
    public String toString() {
        return "DailyTicketPayReqDTO{thirdUserId='" + thirdUserId + "', orderType='" + orderType
                + "', orderNo='" + orderNo + "', payChannelCode='" + payChannelCode
                + "', phone='" + phone + "', channelType='" + channelType + "', cardId='" + cardId + "'}";
    }
}
