package com.chinasofti.huateng.collectpay.entity;

/** APP退款订单实体类。 */
public class AppRefundOrder {

    /** 退款单号（主键）。 */
    private String refundNo;

    /** 原支付订单号。 */
    private String payOrderNo;

    /** 商户退款单号。 */
    private String merchantRefundNo;

    /** 渠道退款单号。 */
    private String channelRefundNo;

    /** 退款金额（分）。 */
    private String refundAmount;

    /** 退款原因。 */
    private String refundReason;

    /** 退款状态。 */
    private String refundStatus;

    /** 退款状态描述。 */
    private String refundMsg;

    /** 退款时间。 */
    private String refundTime;

    /** 创建时间。 */
    private String createTime;

    /** 更新时间。 */
    private String updateTime;

    /** 预留字段1。 */
    private String rsv1;

    /** 预留字段2。 */
    private String rsv2;

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

    public String getRefundAmount() {
        return refundAmount;
    }

    public void setRefundAmount(String refundAmount) {
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

    public String getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(String updateTime) {
        this.updateTime = updateTime;
    }

    public String getRsv1() {
        return rsv1;
    }

    public void setRsv1(String rsv1) {
        this.rsv1 = rsv1;
    }

    public String getRsv2() {
        return rsv2;
    }

    public void setRsv2(String rsv2) {
        this.rsv2 = rsv2;
    }

    @Override
    public String toString() {
        return "BomRefundOrder{" +
                "refundNo='" + refundNo + '\'' +
                ", payOrderNo='" + payOrderNo + '\'' +
                ", merchantRefundNo='" + merchantRefundNo + '\'' +
                ", channelRefundNo='" + channelRefundNo + '\'' +
                ", refundAmount='" + refundAmount + '\'' +
                ", refundReason='" + refundReason + '\'' +
                ", refundStatus='" + refundStatus + '\'' +
                ", refundMsg='" + refundMsg + '\'' +
                ", refundTime='" + refundTime + '\'' +
                ", createTime='" + createTime + '\'' +
                ", updateTime='" + updateTime + '\'' +
                ", rsv1='" + rsv1 + '\'' +
                ", rsv2='" + rsv2 + '\'' +
                '}';
    }
}