package com.chinasofti.huateng.model.app.dailyticket;

import java.text.SimpleDateFormat;
import java.util.Date;


public class DailyTicketPayQueryResult extends DailyTicketBaseResult {

    private String tradeNo;


    private String payResult;


    private Integer payAmount;


    private String payDate;


    private String discountInfo;


    private String payChannel;


    private Integer channelDiscount;


    private Integer couponDiscount;

    public String getTradeNo() {
        return tradeNo;
    }

    public void setTradeNo(String tradeNo) {
        this.tradeNo = tradeNo;
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

    public String getPayDate() {
        return payDate;
    }

    public void setPayDate(Date payDate) {
        this.payDate = payDate == null ? null : new SimpleDateFormat("yyyyMMddHHmmss").format(payDate);
    }

    public String getDiscountInfo() {
        return discountInfo;
    }

    public void setDiscountInfo(String discountInfo) {
        this.discountInfo = discountInfo;
    }

    public String getPayChannel() {
        return payChannel;
    }

    public void setPayChannel(String payChannel) {
        this.payChannel = payChannel;
    }

    public Integer getChannelDiscount() {
        return channelDiscount;
    }

    public void setChannelDiscount(Integer channelDiscount) {
        this.channelDiscount = channelDiscount;
    }

    public Integer getCouponDiscount() {
        return couponDiscount;
    }

    public void setCouponDiscount(Integer couponDiscount) {
        this.couponDiscount = couponDiscount;
    }
}
