package com.chinasofti.huateng.model.ticket;

import com.chinasofti.huateng.common.response.CommonResult;


public class NotifyVerifyResultRespDTO extends CommonResult {


    private String ticketStatus;


    private String orderExpType;


    private String offlineFlag;


    private String companionFlag;


    private String ticketCode;


    private Integer countingTimes;


    private String countingFlag;


    private String attributableParty;


    private String receivingParty;


    private String payChannelCode;


    private String discountFee;


    private String discountInfo;


    private String debitRequestResult;


    private String requestSignSeq;


    private String payUserId;

    public String getTicketStatus() {
        return ticketStatus;
    }

    public void setTicketStatus(String ticketStatus) {
        this.ticketStatus = ticketStatus;
    }

    public String getOrderExpType() {
        return orderExpType;
    }

    public void setOrderExpType(String orderExpType) {
        this.orderExpType = orderExpType;
    }

    public String getOfflineFlag() {
        return offlineFlag;
    }

    public void setOfflineFlag(String offlineFlag) {
        this.offlineFlag = offlineFlag;
    }

    public String getCompanionFlag() {
        return companionFlag;
    }

    public void setCompanionFlag(String companionFlag) {
        this.companionFlag = companionFlag;
    }

    public String getTicketCode() { return ticketCode; }
    public void setTicketCode(String ticketCode) { this.ticketCode = ticketCode; }
    public Integer getCountingTimes() { return countingTimes; }
    public void setCountingTimes(Integer countingTimes) { this.countingTimes = countingTimes; }
    public String getCountingFlag() { return countingFlag; }
    public void setCountingFlag(String countingFlag) { this.countingFlag = countingFlag; }
    public String getAttributableParty() { return attributableParty; }
    public void setAttributableParty(String attributableParty) { this.attributableParty = attributableParty; }
    public String getReceivingParty() { return receivingParty; }
    public void setReceivingParty(String receivingParty) { this.receivingParty = receivingParty; }
    public String getPayChannelCode() { return payChannelCode; }
    public void setPayChannelCode(String payChannelCode) { this.payChannelCode = payChannelCode; }
    public String getDiscountFee() { return discountFee; }
    public void setDiscountFee(String discountFee) { this.discountFee = discountFee; }
    public String getDiscountInfo() { return discountInfo; }
    public void setDiscountInfo(String discountInfo) { this.discountInfo = discountInfo; }
    public String getDebitRequestResult() { return debitRequestResult; }
    public void setDebitRequestResult(String debitRequestResult) { this.debitRequestResult = debitRequestResult; }
    public String getRequestSignSeq() { return requestSignSeq; }
    public void setRequestSignSeq(String requestSignSeq) { this.requestSignSeq = requestSignSeq; }
    public String getPayUserId() { return payUserId; }
    public void setPayUserId(String payUserId) { this.payUserId = payUserId; }
}
