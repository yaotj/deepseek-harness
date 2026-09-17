package com.chinasofti.huateng.model.app;

/**
 * 支付 API 1.1 请求支付业务参数。
 */
public class RequestPayReqDTO {
    private String orderNo;
    private String scene;
    private String paymentVendor;
    private Integer amount;
    private String industryType;
    private String subject;
    private String body;
    private String requestSignSeq;
    /** 钱包支付账户标识。钱包渠道不使用 requestSignSeq，改用该字段透传扣款主体。 */
    private String payUserId;
    private String thirdUserId;
    private String cardId;
    private String cardType;
    private String industryDetail;
    private Long orderTimeOut;
    private String authCode;
    private String notifyUrl;
    private String returnUrl;
    private String ipAddress;
    private String remark;

    /**
     * 支付通道编码（signChannelCode），由 gate-txn-pay-server 透传。
     */
    private String payChannelCode;

    /**
     * 优惠金额（分），由 gate-txn-pay-server 透传。
     */
    private Integer discountFee;

    /**
     * 优惠详情 JSON 数组，由 gate-txn-pay-server 透传。
     */
    private String discountInfo;

    /**
     * 交易日期 YYYYMMDD，由发起方（gate-txn-pay-server 取 GATE_TXN_PAY.TXN_DATE）透传。
     */
    private String txnDate;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getScene() {
        return scene;
    }

    public void setScene(String scene) {
        this.scene = scene;
    }

    public String getPaymentVendor() {
        return paymentVendor;
    }

    public void setPaymentVendor(String paymentVendor) {
        this.paymentVendor = paymentVendor;
    }

    public Integer getAmount() {
        return amount;
    }

    public void setAmount(Integer amount) {
        this.amount = amount;
    }

    public String getIndustryType() {
        return industryType;
    }

    public void setIndustryType(String industryType) {
        this.industryType = industryType;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getRequestSignSeq() {
        return requestSignSeq;
    }

    public void setRequestSignSeq(String requestSignSeq) {
        this.requestSignSeq = requestSignSeq;
    }

    public String getPayUserId() {
        return payUserId;
    }

    public void setPayUserId(String payUserId) {
        this.payUserId = payUserId;
    }

    public String getThirdUserId() {
        return thirdUserId;
    }

    public void setThirdUserId(String thirdUserId) {
        this.thirdUserId = thirdUserId;
    }

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    public String getCardType() {
        return cardType;
    }

    public void setCardType(String cardType) {
        this.cardType = cardType;
    }

    public String getIndustryDetail() {
        return industryDetail;
    }

    public void setIndustryDetail(String industryDetail) {
        this.industryDetail = industryDetail;
    }

    public Long getOrderTimeOut() {
        return orderTimeOut;
    }

    public void setOrderTimeOut(Long orderTimeOut) {
        this.orderTimeOut = orderTimeOut;
    }

    public String getAuthCode() {
        return authCode;
    }

    public void setAuthCode(String authCode) {
        this.authCode = authCode;
    }

    public String getNotifyUrl() {
        return notifyUrl;
    }

    public void setNotifyUrl(String notifyUrl) {
        this.notifyUrl = notifyUrl;
    }

    public String getReturnUrl() {
        return returnUrl;
    }

    public void setReturnUrl(String returnUrl) {
        this.returnUrl = returnUrl;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Integer getDiscountFee() { return discountFee; }
    public void setDiscountFee(Integer discountFee) { this.discountFee = discountFee; }
    public String getDiscountInfo() { return discountInfo; }
    public void setDiscountInfo(String discountInfo) { this.discountInfo = discountInfo; }
    public String getTxnDate() { return txnDate; }
    public void setTxnDate(String txnDate) { this.txnDate = txnDate; }

    @Override
    public String toString() {
        return "RequestPayReqDTO{" +
                "orderNo='" + orderNo + '\'' +
                ", scene='" + scene + '\'' +
                ", paymentVendor='" + paymentVendor + '\'' +
                ", amount=" + amount +
                ", industryType='" + industryType + '\'' +
                ", subject='" + subject + '\'' +
                ", requestSignSeq='" + requestSignSeq + '\'' +
                ", thirdUserId='" + thirdUserId + '\'' +
                ", cardId='" + cardId + '\'' +
                ", cardType='" + cardType + '\'' +
                ", txnDate='" + txnDate + '\'' +
                '}';
    }
}
