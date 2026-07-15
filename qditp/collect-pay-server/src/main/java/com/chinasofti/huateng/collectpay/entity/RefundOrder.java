package com.chinasofti.huateng.collectpay.entity;

import java.time.LocalDateTime;

/**
 * 退款记录表实体（tbl_refund_order）。
 */
public class RefundOrder {
    /**
     * 退款单号。
     */
    private String refundNo;

    /**
     * 原支付订单号。
     */
    private String payOrderNo;

    /**
     * 商户退款单号。
     */
    private String merchantRefundNo;

    /**
     * 渠道退款单号。
     */
    private String channelRefundNo;

    /**
     * 退款金额（分）。
     */
    private Integer refundAmount;

    /**
     * 退款原因。
     */
    private String refundReason;

    /**
     * 退款状态：0-退款中，1-退款成功，2-退款失败。
     */
    private String refundStatus;
    private String refundMsg;

    /**
     * 退款时间。
     */
    private String refundTime;

    /**
     * 创建时间。
     */
    private String createTime;

    public String getRefundNo() {
        return refundNo;
    }

    public void setRefundNo(String refundNo) {
        this.refundNo = refundNo;
    }

    public String getPayOrderNo() {
        return payOrderNo;
    }

    public void setPayOrderNo(String payOrderNo) {
        this.payOrderNo = payOrderNo;
    }

    public String getMerchantRefundNo() {
        return merchantRefundNo;
    }

    public void setMerchantRefundNo(String merchantRefundNo) {
        this.merchantRefundNo = merchantRefundNo;
    }

    public String getChannelRefundNo() {
        return channelRefundNo;
    }

    public void setChannelRefundNo(String channelRefundNo) {
        this.channelRefundNo = channelRefundNo;
    }

    public Integer getRefundAmount() {
        return refundAmount;
    }

    public void setRefundAmount(Integer refundAmount) {
        this.refundAmount = refundAmount;
    }

    public String getRefundReason() {
        return refundReason;
    }

    public void setRefundReason(String refundReason) {
        this.refundReason = refundReason;
    }

    public String getRefundStatus() {
        return refundStatus;
    }

    public void setRefundStatus(String refundStatus) {
        this.refundStatus = refundStatus;
    }

    public String getRefundMsg() {
        return refundMsg;
    }

    public void setRefundMsg(String refundMsg) {
        this.refundMsg = refundMsg;
    }

    public String getRefundTime() {
        return refundTime;
    }

    public void setRefundTime(String refundTime) {
        this.refundTime = refundTime;
    }

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "RefundOrder{" +
                "refundNo='" + refundNo + '\'' +
                ", payOrderNo='" + payOrderNo + '\'' +
                ", merchantRefundNo='" + merchantRefundNo + '\'' +
                ", channelRefundNo='" + channelRefundNo + '\'' +
                ", refundAmount=" + refundAmount +
                ", refundReason='" + refundReason + '\'' +
                ", refundStatus='" + refundStatus + '\'' +
                ", refundMsg='" + refundMsg + '\'' +
                ", refundTime='" + refundTime + '\'' +
                ", createTime=" + createTime +
                '}';
    }
}
