package com.chinasofti.huateng.facepay.api.paycenter;

/**
 * 支付结果回调的业务报文（{@code bizData} 内容），字段与旧 {@code PayNoticeReqDTO} 逐字一致。
 *
 * <p><b>两个订单号极易搞混</b>：{@code orderNo} 是<b>支付中心侧</b>订单号，
 * {@code merchantOrderNo} 才是<b>我方</b>订单号——定位本地订单 MUST 用 {@code merchantOrderNo}。
 * 旧实现 {@code payNotice} 里 {@code selectByOrderNo(request.getMerchantOrderNo())} 即此意，
 * 而同一方法的日志却打 {@code request.getOrderNo()}，读日志时容易被误导。</p>
 *
 * <p>金额单位分；{@code status} 取值同 {@code PayCenterStatus}。</p>
 */
public class PayNoticeReqDTO {

    /** 支付中心订单号。 */
    private String orderNo;

    /** 我方订单号，等于 F2F_ORDER.ORDER_NO。 */
    private String merchantOrderNo;

    /** 渠道订单号（微信/支付宝侧）。 */
    private String channelOrderNo;

    /** 支付状态：SUCCESS / FAILED / ORDERED / UNPAID。 */
    private String status;

    /** 支付时间。 */
    private String payTime;

    /** 订单总金额，单位分。 */
    private String totalAmount;

    /** 现金支付金额，单位分。 */
    private String cashAmount;

    /** 优惠金额，单位分。 */
    private String couponAmount;

    /** 付款用户标识。 */
    private String payUserId;

    /** 支付方式（渠道码），回填到 F2F_PAYMENT.PAY_CHANNEL_CODE。 */
    private String paymentVendor;

    /** 扩展字段。 */
    private String options;

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

    public String getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(String totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getCashAmount() {
        return cashAmount;
    }

    public void setCashAmount(String cashAmount) {
        this.cashAmount = cashAmount;
    }

    public String getCouponAmount() {
        return couponAmount;
    }

    public void setCouponAmount(String couponAmount) {
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

    @Override
    public String toString() {
        return "PayNoticeReqDTO{orderNo=" + orderNo
                + ", merchantOrderNo=" + merchantOrderNo
                + ", channelOrderNo=" + channelOrderNo
                + ", status=" + status
                + ", payTime=" + payTime
                + ", totalAmount=" + totalAmount
                + ", paymentVendor=" + paymentVendor
                + '}';
    }
}
