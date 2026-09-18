package com.chinasofti.huateng.model.app;

/**
 * 支付 API 5.1 支付回调业务参数。
 */
public class ReceivePayResultReqDTO {
    private String orderNo;
    private String merchantOrderNo;
    private String channelOrderNo;
    private String status;
    private String payTime;
    private Integer totalAmount;
    private Integer cashAmount;
    private Integer couponAmount;
    private String payUserId;
    private String paymentVendor;
    private String options;
    /**
     * 渠道优惠详情（网关文档 V1.2 新增），JSON 数组原文，元素为 {@code type} / {@code name} / {@code amount}。
     * 只原样接收落库、不解析成嵌套 DTO：这是对外契约，NEVER 加字段或建结构类。
     * 与 {@code RequestPayReqDTO.discountInfo}（闸机侧自算优惠）**不是同一个口径**，落库列也不同，NEVER 混用。
     */
    private String discountInfo;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getMerchantOrderNo() {
        return merchantOrderNo;
    }

    public void setMerchantOrderNo(String merchantOrderNo) {
        this.merchantOrderNo = merchantOrderNo;
    }

    public String getChannelOrderNo() {
        return channelOrderNo;
    }

    public void setChannelOrderNo(String channelOrderNo) {
        this.channelOrderNo = channelOrderNo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPayTime() {
        return payTime;
    }

    public void setPayTime(String payTime) {
        this.payTime = payTime;
    }

    public Integer getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(Integer totalAmount) {
        this.totalAmount = totalAmount;
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

    public String getPayUserId() {
        return payUserId;
    }

    public void setPayUserId(String payUserId) {
        this.payUserId = payUserId;
    }

    public String getPaymentVendor() {
        return paymentVendor;
    }

    public void setPaymentVendor(String paymentVendor) {
        this.paymentVendor = paymentVendor;
    }

    public String getOptions() {
        return options;
    }

    public void setOptions(String options) {
        this.options = options;
    }

    public String getDiscountInfo() {
        return discountInfo;
    }

    public void setDiscountInfo(String discountInfo) {
        this.discountInfo = discountInfo;
    }

    @Override
    public String toString() {
        return "ReceivePayResultReqDTO{" +
                "orderNo='" + orderNo + '\'' +
                ", merchantOrderNo='" + merchantOrderNo + '\'' +
                ", channelOrderNo='" + channelOrderNo + '\'' +
                ", status='" + status + '\'' +
                ", payTime='" + payTime + '\'' +
                ", totalAmount=" + totalAmount +
                ", cashAmount=" + cashAmount +
                ", couponAmount=" + couponAmount +
                ", paymentVendor='" + paymentVendor + '\'' +
                '}';
    }
}
