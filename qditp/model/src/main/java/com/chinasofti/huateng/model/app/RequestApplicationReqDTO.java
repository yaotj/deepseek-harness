package com.chinasofti.huateng.model.app;


public class RequestApplicationReqDTO {


    private String thirdUserId;


    private String thirdPayId;


    private String channel;


    private String reqContractNo;


    private String cardType;


    private String msisdn;

    private String userName;


    private String userId;

    private String extend1;

    private String extend2;


    private String ticketCard;


    private String companionFlag;


    private String ticketLimit;


    private String cardIssueCode;

    public String getThirdUserId() {
        return thirdUserId;
    }

    public void setThirdUserId(String thirdUserId) {
        this.thirdUserId = thirdUserId;
    }

    public String getThirdPayId() {
        return thirdPayId;
    }

    public void setThirdPayId(String thirdPayId) {
        this.thirdPayId = thirdPayId;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getReqContractNo() {
        return reqContractNo;
    }

    public void setReqContractNo(String reqContractNo) {
        this.reqContractNo = reqContractNo;
    }

    public String getCardType() {
        return cardType;
    }

    public void setCardType(String cardType) {
        this.cardType = cardType;
    }

    public String getMsisdn() {
        return msisdn;
    }

    public void setMsisdn(String msisdn) {
        this.msisdn = msisdn;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getExtend1() {
        return extend1;
    }

    public void setExtend1(String extend1) {
        this.extend1 = extend1;
    }

    public String getExtend2() {
        return extend2;
    }

    public void setExtend2(String extend2) {
        this.extend2 = extend2;
    }

    public String getTicketCard() {
        return ticketCard;
    }

    public void setTicketCard(String ticketCard) {
        this.ticketCard = ticketCard;
    }

    public String getCompanionFlag() {
        return companionFlag;
    }

    public void setCompanionFlag(String companionFlag) {
        this.companionFlag = companionFlag;
    }

    public String getTicketLimit() {
        return ticketLimit;
    }

    public void setTicketLimit(String ticketLimit) {
        this.ticketLimit = ticketLimit;
    }

    public String getCardIssueCode() {
        return cardIssueCode;
    }

    public void setCardIssueCode(String cardIssueCode) {
        this.cardIssueCode = cardIssueCode;
    }

    @Override
    public String toString() {
        return "RequestApplicationReqDTO{thirdUserId='" + thirdUserId + "', thirdPayId='" + thirdPayId
                + "', channel='" + channel + "', reqContractNo='" + reqContractNo + "', cardType='" + cardType
                + "', msisdn='" + msisdn + "', userName='" + userName + "', userId='" + userId
                + "', extend1='" + extend1 + "', extend2='" + extend2 + "', ticketCard='" + ticketCard
                + "', companionFlag='" + companionFlag + "', ticketLimit='" + ticketLimit
                + "', cardIssueCode='" + cardIssueCode + "'}";
    }
}
