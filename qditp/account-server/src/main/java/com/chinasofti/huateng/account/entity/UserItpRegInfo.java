package com.chinasofti.huateng.account.entity;

import java.time.LocalDateTime;


public class UserItpRegInfo {

    private Integer id;


    private String cardId;


    private String cardType;


    private String itpCardType;


    private String thirdUserId;


    private String msisdn;


    private LocalDateTime regTms;


    private Integer delYn;


    private String delThirdUserId;


    private LocalDateTime unRegTms;


    private String userName;


    private String userId;


    private String cardIssueCode;


    private String issueOrgCode;


    private String thirdPayId;


    private String channel;


    private String reqContractNo;


    private String hceData;


    private String companionFlag;

    private String ticketLimit;

    public String getTicketLimit() {
        return ticketLimit;
    }

    public void setTicketLimit(String ticketLimit) {
        this.ticketLimit = ticketLimit;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
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

    public String getItpCardType() {
        return itpCardType;
    }

    public void setItpCardType(String itpCardType) {
        this.itpCardType = itpCardType;
    }

    public String getThirdUserId() {
        return thirdUserId;
    }

    public void setThirdUserId(String thirdUserId) {
        this.thirdUserId = thirdUserId;
    }

    public String getMsisdn() {
        return msisdn;
    }

    public void setMsisdn(String msisdn) {
        this.msisdn = msisdn;
    }

    public LocalDateTime getRegTms() {
        return regTms;
    }

    public void setRegTms(LocalDateTime regTms) {
        this.regTms = regTms;
    }

    public Integer getDelYn() {
        return delYn;
    }

    public void setDelYn(Integer delYn) {
        this.delYn = delYn;
    }


    public boolean isActive() {
        return delYn != null && delYn == 1;
    }


    public boolean isCanceled() {
        return delYn != null && delYn == 0;
    }

    public String getDelThirdUserId() {
        return delThirdUserId;
    }

    public void setDelThirdUserId(String delThirdUserId) {
        this.delThirdUserId = delThirdUserId;
    }

    public LocalDateTime getUnRegTms() {
        return unRegTms;
    }

    public void setUnRegTms(LocalDateTime unRegTms) {
        this.unRegTms = unRegTms;
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

    public String getCardIssueCode() {
        return cardIssueCode;
    }

    public void setCardIssueCode(String cardIssueCode) {
        this.cardIssueCode = cardIssueCode;
    }

    public String getIssueOrgCode() {
        return issueOrgCode;
    }

    public void setIssueOrgCode(String issueOrgCode) {
        this.issueOrgCode = issueOrgCode;
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

    public String getHceData() {
        return hceData;
    }

    public void setHceData(String hceData) {
        this.hceData = hceData;
    }

    public String getCompanionFlag() {
        return companionFlag;
    }

    public void setCompanionFlag(String companionFlag) {
        this.companionFlag = companionFlag;
    }
}
