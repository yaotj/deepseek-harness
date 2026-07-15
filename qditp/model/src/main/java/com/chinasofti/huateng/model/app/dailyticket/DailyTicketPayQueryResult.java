package com.chinasofti.huateng.model.app.dailyticket;

import java.util.Date;

/**
 * IF8A-62 日票支付结果查询响应参数。
 */
public class DailyTicketPayQueryResult extends DailyTicketBaseResult {
    /**
     * 第三方支付交易号。
     */
    private String tradeNo;

    /**
     * 支付结果，success表示成功。
     */
    private String payResult;

    /**
     * 支付金额，单位分。
     */
    private Integer payAmount;

    /**
     * 支付完成时间。
     */
    private Date payDate;

    /**
     * 优惠信息。
     */
    private String discountInfo;

    /**
     * 支付渠道。
     */
    private String payChannel;

    /**
     * 渠道优惠金额，单位分。
     */
    private Integer channelDiscount;

    /**
     * 优惠券优惠金额，单位分。
     */
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

    public Date getPayDate() {
        return payDate;
    }

    public void setPayDate(Date payDate) {
        this.payDate = payDate;
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
