package com.chinasofti.huateng.model.alipaytrip;

/**
 * 支付宝出行-支付结果回调请求参数。
 */
public class AlipayTripPayNotifyReqDTO {

    /**
     * 原订单号
     */
    private String orderNo;

    /**
     * 支付渠道订单号
     */
    private String channelVoucherId;

    /**
     * 支付金额，单位分
     */
    private String transAmount;

    /**
     * 交易时间 yyyy-MM-dd HH:mm:ss
     */
    private String transTime;

    /**
     * 交易状态 1-成功 2-失败
     */
    private String transStatus;

    /**
     * 支付宝逻辑卡号：卡机构编号+地铁逻辑卡号
     */
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
