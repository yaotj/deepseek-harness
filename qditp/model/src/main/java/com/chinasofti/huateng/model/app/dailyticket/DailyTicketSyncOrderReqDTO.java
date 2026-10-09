package com.chinasofti.huateng.model.app.dailyticket;

import java.util.Date;

/**
 * IF8A-72 小程序票状态同步请求参数。
 */
public class DailyTicketSyncOrderReqDTO {
    /** 订单号。 */
    private String orderNo;

    /** 订单类型：1-日票，2-旅游票。 */
    private String orderType;

    /** 事件：1-支付成功，2-退款成功。 */
    private String event;

    /** 事件发生时间。 */
    private Date eventTime;

    /** 支付渠道。 */
    private String payChannel;

    /** 优惠信息。 */
    private String discountInfo;

    /** 支付金额，单位分。 */
    private Integer payAmount;

    /** 优惠金额，单位分。 */
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
