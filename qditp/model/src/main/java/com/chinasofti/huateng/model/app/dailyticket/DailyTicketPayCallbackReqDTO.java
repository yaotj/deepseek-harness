package com.chinasofti.huateng.model.app.dailyticket;

import java.util.Date;


public class DailyTicketPayCallbackReqDTO {

    private String orderNo;


    private String tradeNo;


    private String paymentOrderNo;


    private String payResult;


    private Integer payAmount;


    private Date payDate;


    private String payChannel;


    private Integer cashAmount;


    private Integer couponAmount;


    private String rawBody;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getTradeNo() {
        return tradeNo;
    }

    public void setTradeNo(String tradeNo) {
        this.tradeNo = tradeNo;
    }

    public String getPaymentOrderNo() {
        return paymentOrderNo;
    }

    public void setPaymentOrderNo(String paymentOrderNo) {
        this.paymentOrderNo = paymentOrderNo;
    }

    public String getPayResult() {
        return payResult;
    }

    public void setPayResult(String payResult) {
        this.payResult = payResult;
    }

    public Integer getPayAmount() {
        return payAmount;
    }

    public void setPayAmount(Integer payAmount) {
        this.payAmount = payAmount;
    }

    public Date getPayDate() {
        return payDate;
    }

    public void setPayDate(Date payDate) {
        this.payDate = payDate;
    }

    public String getPayChannel() {
        return payChannel;
    }

    public void setPayChannel(String payChannel) {
        this.payChannel = payChannel;
    }

    public String getRawBody() {
        return rawBody;
    }

    public void setRawBody(String rawBody) {
        this.rawBody = rawBody;
    }

    public Integer getCashAmount() {
        return cashAmount;
    }

    public void setCashAmount(Integer cashAmount) {
        this.cashAmount = cashAmount;
    }

    public Integer getCouponAmount() {
        return couponAmount;
    }

    public void setCouponAmount(Integer couponAmount) {
        this.couponAmount = couponAmount;
    }

    @Override
    public String toString() {
        return "DailyTicketPayCallbackReqDTO{orderNo='" + orderNo + "', tradeNo='" + tradeNo
                + "', paymentOrderNo='" + paymentOrderNo + "', payResult='" + payResult
                + "', payAmount=" + payAmount + ", payDate=" + payDate + "', payChannel='" + payChannel
                + "', cashAmount=" + cashAmount + ", couponAmount=" + couponAmount
                + ", rawBody='" + (rawBody != null ? "[length=" + rawBody.length() + "]" : null) + "'}";
    }
}
