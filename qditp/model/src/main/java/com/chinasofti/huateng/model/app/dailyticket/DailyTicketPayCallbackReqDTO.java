package com.chinasofti.huateng.model.app.dailyticket;

import java.util.Date;

/**
 * 日票支付结果回调内部转发参数。
 */
public class DailyTicketPayCallbackReqDTO {
    /**
     * 日票订单号。
     */
    private String orderNo;

    /**
     * 第三方支付交易号。
     */
    private String tradeNo;

    /**
     * 支付结果，success/SUCCESS表示成功。
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
     * 支付渠道。
     */
    private String payChannel;

    /**
     * 原始回调报文，便于审计和排障。
     */
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
}
