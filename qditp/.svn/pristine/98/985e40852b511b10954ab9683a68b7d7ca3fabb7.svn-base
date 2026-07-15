package com.chinasofti.huateng.paysign.entity;

import java.time.LocalDateTime;

/**
 * 支付/退款回调流水。
 *
 * <p>每次支付平台回调都插入一条记录，避免重复回调覆盖原文。当前支付订单的最终态由
 * {@code PAY_TXN_DETAIL} 或 {@code PAY_REFUND_DETAIL} 保存。</p>
 */
public class PayCallbackLog {
    private Long id;
    private String orderNo;
    private String callbackType;
    private String callbackStatus;
    private String refundOrderNo;
    private String merchantOrderNo;
    private String channelOrderNo;
    private String merchantRefundNo;
    private String refundNo;
    private String channelRefundNo;
    private String payTime;
    private String refundTime;
    private Integer totalAmount;
    private Integer cashAmount;
    private Integer couponAmount;
    private Integer refundAmount;
    private String payUserId;
    private String paymentVendor;
    private String txnDate;
    private String rawBody;
    private String handleStatus;
    private String handleMsg;
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getCallbackType() { return callbackType; }
    public void setCallbackType(String callbackType) { this.callbackType = callbackType; }
    public String getCallbackStatus() { return callbackStatus; }
    public void setCallbackStatus(String callbackStatus) { this.callbackStatus = callbackStatus; }
    public String getRefundOrderNo() { return refundOrderNo; }
    public void setRefundOrderNo(String refundOrderNo) { this.refundOrderNo = refundOrderNo; }
    public String getMerchantOrderNo() { return merchantOrderNo; }
    public void setMerchantOrderNo(String merchantOrderNo) { this.merchantOrderNo = merchantOrderNo; }
    public String getChannelOrderNo() { return channelOrderNo; }
    public void setChannelOrderNo(String channelOrderNo) { this.channelOrderNo = channelOrderNo; }
    public String getMerchantRefundNo() { return merchantRefundNo; }
    public void setMerchantRefundNo(String merchantRefundNo) { this.merchantRefundNo = merchantRefundNo; }
    public String getRefundNo() { return refundNo; }
    public void setRefundNo(String refundNo) { this.refundNo = refundNo; }
    public String getChannelRefundNo() { return channelRefundNo; }
    public void setChannelRefundNo(String channelRefundNo) { this.channelRefundNo = channelRefundNo; }
    public String getPayTime() { return payTime; }
    public void setPayTime(String payTime) { this.payTime = payTime; }
    public String getRefundTime() { return refundTime; }
    public void setRefundTime(String refundTime) { this.refundTime = refundTime; }
    public Integer getTotalAmount() { return totalAmount; }
    public void setTotalAmount(Integer totalAmount) { this.totalAmount = totalAmount; }
    public Integer getCashAmount() { return cashAmount; }
    public void setCashAmount(Integer cashAmount) { this.cashAmount = cashAmount; }
    public Integer getCouponAmount() { return couponAmount; }
    public void setCouponAmount(Integer couponAmount) { this.couponAmount = couponAmount; }
    public Integer getRefundAmount() { return refundAmount; }
    public void setRefundAmount(Integer refundAmount) { this.refundAmount = refundAmount; }
    public String getPayUserId() { return payUserId; }
    public void setPayUserId(String payUserId) { this.payUserId = payUserId; }
    public String getPaymentVendor() { return paymentVendor; }
    public void setPaymentVendor(String paymentVendor) { this.paymentVendor = paymentVendor; }
    public String getTxnDate() { return txnDate; }
    public void setTxnDate(String txnDate) { this.txnDate = txnDate; }
    public String getRawBody() { return rawBody; }
    public void setRawBody(String rawBody) { this.rawBody = rawBody; }
    public String getHandleStatus() { return handleStatus; }
    public void setHandleStatus(String handleStatus) { this.handleStatus = handleStatus; }
    public String getHandleMsg() { return handleMsg; }
    public void setHandleMsg(String handleMsg) { this.handleMsg = handleMsg; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
