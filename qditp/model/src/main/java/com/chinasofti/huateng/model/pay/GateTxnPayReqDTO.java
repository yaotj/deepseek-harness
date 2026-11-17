package com.chinasofti.huateng.model.pay;

import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;


public class GateTxnPayReqDTO extends NotifyVerifyResultReqDTO {


    private String ticketStatus;


    private String orderExpType;


    private String offlineFlag;


    private String entryStationName;


    private String exitStationName;


    private String companionFlag;


    private String ticketCode;


    private Integer countingTimes;


    private String countingFlag;


    private String attributableParty;


    private String receivingParty;


    private String payChannelCode;


    private Integer discountFee;


    private String discountInfo;

    private String payUserId;


    private String industryDetail;


    public static GateTxnPayReqDTO fromVerifyResult(NotifyVerifyResultReqDTO request) {
        GateTxnPayReqDTO payRequest = new GateTxnPayReqDTO();
        payRequest.setDeviceId(request.getDeviceId());
        payRequest.setItpUserId(request.getItpUserId());
        payRequest.setTrxType(request.getTrxType());
        payRequest.setIssueChannelCode(request.getIssueChannelCode());
        payRequest.setSignChannelCode(request.getSignChannelCode());
        payRequest.setCardId(request.getCardId());
        payRequest.setCardType(request.getCardType());
        payRequest.setHandleDateTime(request.getHandleDateTime());
        payRequest.setHandleStationCode(request.getHandleStationCode());
        payRequest.setTrxAmount(request.getTrxAmount());
        payRequest.setOvertimeAmount(request.getOvertimeAmount());
        payRequest.setLastTicketStatus(request.getLastTicketStatus());
        payRequest.setHandleResultCode(request.getHandleResultCode());
        payRequest.setLastHandleStationCode(request.getLastHandleStationCode());
        payRequest.setLastHandleDateTime(request.getLastHandleDateTime());
        payRequest.setTicketTransSeq(request.getTicketTransSeq());
        payRequest.setReserve1(request.getReserve1());
        payRequest.setReserve2(request.getReserve2());
        payRequest.setChannelType(request.getChannelType());
        payRequest.setCompanionFlag(request.getCompanionFlag());
        payRequest.setAdviceOpt(request.getAdviceOpt());
        return payRequest;
    }

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

    public String getEntryStationName() {
        return entryStationName;
    }

    public void setEntryStationName(String entryStationName) {
        this.entryStationName = entryStationName;
    }

    public String getExitStationName() {
        return exitStationName;
    }

    public void setExitStationName(String exitStationName) {
        this.exitStationName = exitStationName;
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
    public Integer getDiscountFee() { return discountFee; }
    public void setDiscountFee(Integer discountFee) { this.discountFee = discountFee; }
    public String getDiscountInfo() { return discountInfo; }
    public void setDiscountInfo(String discountInfo) { this.discountInfo = discountInfo; }
    public String getPayUserId() { return payUserId; }
    public void setPayUserId(String payUserId) { this.payUserId = payUserId; }
    public String getIndustryDetail() { return industryDetail; }
    public void setIndustryDetail(String industryDetail) { this.industryDetail = industryDetail; }
}
