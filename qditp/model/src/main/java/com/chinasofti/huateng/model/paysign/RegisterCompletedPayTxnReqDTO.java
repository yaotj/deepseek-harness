package com.chinasofti.huateng.model.paysign;


public class RegisterCompletedPayTxnReqDTO {

    private String orderNo;

    private String txnDate;

    private String thirdUserId;

    private String cardId;

    private String cardType;

    private String paymentVendor;

    private String requestSignSeq;

    private String payUserId;

    private Integer amount;

    private String reason;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getTxnDate() {
        return txnDate;
    }

    public void setTxnDate(String txnDate) {
        this.txnDate = txnDate;
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

    public String getPaymentVendor() {
        return paymentVendor;
    }

    public void setPaymentVendor(String paymentVendor) {
        this.paymentVendor = paymentVendor;
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

    public Integer getAmount() {
        return amount;
    }

    public void setAmount(Integer amount) {
        this.amount = amount;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    @Override
    public String toString() {
        return "RegisterCompletedPayTxnReqDTO{orderNo='" + orderNo + "', txnDate='" + txnDate
                + "', thirdUserId='" + thirdUserId + "', cardId='" + cardId + "', cardType='" + cardType
                + "', paymentVendor='" + paymentVendor + "', amount=" + amount + ", reason='" + reason + "'}";
    }
}
