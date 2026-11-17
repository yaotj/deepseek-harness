package com.chinasofti.huateng.model.app.dailyticket;

import java.util.Date;


public class DailyTicketSyncOrderReqDTO {
    private String orderNo;

    private String orderType;

    private String event;

    private Date eventTime;

    private String payChannel;

    private String discountInfo;

    private Integer payAmount;

    private Integer discountAmount;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getOrderType() {
        return orderType;
    }

    public void setOrderType(String orderType) {
        this.orderType = orderType;
    }

    public String getEvent() {
        return event;
    }

    public void setEvent(String event) {
        this.event = event;
    }

    public Date getEventTime() {
        return eventTime;
    }

    public void setEventTime(Date eventTime) {
        this.eventTime = eventTime;
    }

    public String getPayChannel() {
        return payChannel;
    }

    public void setPayChannel(String payChannel) {
        this.payChannel = payChannel;
    }

    public String getDiscountInfo() {
        return discountInfo;
    }

    public void setDiscountInfo(String discountInfo) {
        this.discountInfo = discountInfo;
    }

    public Integer getPayAmount() {
        return payAmount;
    }

    public void setPayAmount(Integer payAmount) {
        this.payAmount = payAmount;
    }

    public Integer getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(Integer discountAmount) {
        this.discountAmount = discountAmount;
    }

    @Override
    public String toString() {
        return "DailyTicketSyncOrderReqDTO{orderNo='" + orderNo + "', orderType='" + orderType
                + "', event='" + event + "', eventTime=" + eventTime + ", payChannel='" + payChannel
                + "', discountInfo='" + discountInfo + "', payAmount=" + payAmount
                + ", discountAmount=" + discountAmount + "}";
    }
}
