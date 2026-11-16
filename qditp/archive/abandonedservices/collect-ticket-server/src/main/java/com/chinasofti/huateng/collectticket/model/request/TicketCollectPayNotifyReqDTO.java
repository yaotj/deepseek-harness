package com.chinasofti.huateng.collectticket.model.request;

/**
 * 支付中心回调取票订单支付结果请求报文。
 * 注意：这里不是 IF8A-11 请求支付报文。
 */
public class TicketCollectPayNotifyReqDTO {
    /**
     * 订单号。
     */
    private String orderNo;

    /**
     * 商户订单号。
     */
    private String merchantOrderNo;

    /**
     * 渠道订单号。
     */
    private String channelOrderNo;

    /**
     * 交易状态。
     */
    private String status;

    /**
     * 支付时间，格式 yyyyMMddHHmmss。
     */
    private String payTime;

    /**
     * 订单总金额，单位分。
     */
    private Integer totalAmount;

    /**
     * 实付金额，单位分。
     */
    private Integer cashAmount;

    /**
     * 优惠金额，单位分。
     */
    private Integer couponAmount;

    /**
     * 支付渠道账户。
     */
    private String payUserId;

    /**
     * 支付方式。
     */
    private String paymentVendor;

    /**
     * 附加参数。
     */
    private String options;

    /**
     * 签名。
     */
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
