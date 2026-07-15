package com.chinasofti.huateng.collectpay.entity;

import java.time.LocalDateTime;

/**
 * App_Pay_Logs 铁运维保取票支付记录表实体。
 */
public class AppPayLogs {
    /**
     * 订单号。
     */
    private String orderNo;

    /**
     * 用户ID。
     */
    private String userId;

    /**
     * 支付类型：0-支付，1-退款。
     */
    private Integer payType;

    /**
     * 渠道编码。
     */
    private String channelCode;

    /**
     * 创建订单时间（创建时间戳）。
     */
    private LocalDateTime createTms;

    /**
     * 支付交易流水号。
     */
    private String tradeNo;

    /**
     * 支付渠道代码编号。
     */
    private String payChannelCode;

    /**
     * 渠道类型：1-APP端（tradeType03），2-ETC端（已弃用，tradeType06）。
     */
    private String channelType;

    /**
     * 支付状态：SUCCESS/FAIL。
     */
    private Integer payResult;

    /**
     * 支付金额。
     */
    private Integer payAmount;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Integer getPayType() {
        return payType;
    }

    public void setPayType(Integer payType) {
        this.payType = payType;
    }

    public String getChannelCode() {
        return channelCode;
    }

    public void setChannelCode(String channelCode) {
        this.channelCode = channelCode;
    }

    public LocalDateTime getCreateTms() {
        return createTms;
    }

    public void setCreateTms(LocalDateTime createTms) {
        this.createTms = createTms;
    }

    public String getTradeNo() {
        return tradeNo;
    }

    public void setTradeNo(String tradeNo) {
        this.tradeNo = tradeNo;
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

    public Integer getPayResult() {
        return payResult;
    }

    public void setPayResult(Integer payResult) {
        this.payResult = payResult;
    }

    public Integer getPayAmount() {
        return payAmount;
    }

    public void setPayAmount(Integer payAmount) {
        this.payAmount = payAmount;
    }

    @Override
    public String toString() {
        return "AppPayLogs{" +
                "orderNo='" + orderNo + '\'' +
                ", userId='" + userId + '\'' +
                ", payType=" + payType +
                ", channelCode='" + channelCode + '\'' +
                ", tradeNo='" + tradeNo + '\'' +
                ", payChannelCode='" + payChannelCode + '\'' +
                ", payResult=" + payResult +
                ", payAmount=" + payAmount +
                '}';
    }
}
