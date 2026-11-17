package com.chinasofti.huateng.collectticket.model.request;


public class TicketCollectPayNotifyReqDTO {

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


    private String sign;

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

    public String getSign() {
        return sign;
    }

    public void setSign(String sign) {
        this.sign = sign;
    }

    @Override
    public String toString() {
        return "TicketCollectPayNotifyReqDTO{" +
                "orderNo='" + orderNo + '\'' +
                ", merchantOrderNo='" + merchantOrderNo + '\'' +
                ", channelOrderNo='" + channelOrderNo + '\'' +
                ", status='" + status + '\'' +
                ", payTime='" + payTime + '\'' +
                ", totalAmount=" + totalAmount +
                ", cashAmount=" + cashAmount +
                ", paymentVendor='" + paymentVendor + '\'' +
                '}';
    }
}
