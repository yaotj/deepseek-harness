package com.chinasofti.huateng.facepay.entity;

/** face-pay-server 对 GATE_TXN_PAY 表的**精简读取**实体。 */
public class GateTxnPay {
    private String orderNo;
    /** 扣费状态：INIT / PROCESSING / SUCCESS / RETRY / FAIL / CLOSED。 */
    private String debitStatus;
    private String thirdUserId;
    private String cardId;
    private String cardType;
    /** 交易日期，VARCHAR2(16) 存 yyyyMMdd，非 DATE。 */
    private String txnDate;
    /** 实付金额，单位分。 */
    private Integer totalAmount;
    private String paymentVendor;
    private String signChannelCode;

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    public String getDebitStatus() { return debitStatus; }
    public void setDebitStatus(String debitStatus) { this.debitStatus = debitStatus; }

    public String getThirdUserId() { return thirdUserId; }
    public void setThirdUserId(String thirdUserId) { this.thirdUserId = thirdUserId; }

    public String getCardId() { return cardId; }
    public void setCardId(String cardId) { this.cardId = cardId; }

    public String getCardType() { return cardType; }
    public void setCardType(String cardType) { this.cardType = cardType; }

    public String getTxnDate() { return txnDate; }
    public void setTxnDate(String txnDate) { this.txnDate = txnDate; }

    public Integer getTotalAmount() { return totalAmount; }
    public void setTotalAmount(Integer totalAmount) { this.totalAmount = totalAmount; }

    public String getPaymentVendor() { return paymentVendor; }
    public void setPaymentVendor(String paymentVendor) { this.paymentVendor = paymentVendor; }

    public String getSignChannelCode() { return signChannelCode; }
    public void setSignChannelCode(String signChannelCode) { this.signChannelCode = signChannelCode; }
}
