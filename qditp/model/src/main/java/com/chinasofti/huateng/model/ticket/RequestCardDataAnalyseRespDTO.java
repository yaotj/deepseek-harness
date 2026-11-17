package com.chinasofti.huateng.model.ticket;

import java.util.List;


public class RequestCardDataAnalyseRespDTO {


    private String retCode;


    private String retMsg;


    private String providerId;


    private String cardIssueDate;


    private String msisdn;


    private String cardId;


    private String cardStatus;


    private String lastLineCode;


    private String lastStationCode;


    private String lastUpdateDate;


    private String lastTransAmout;


    private String lastTicketTransSeq;


    private List<String> adviceOpt;


    private String managerCode;


    private String transAmount;

    public String getRetCode() {
        return retCode;
    }

    public void setRetCode(String retCode) {
        this.retCode = retCode;
    }

    public String getRetMsg() {
        return retMsg;
    }

    public void setRetMsg(String retMsg) {
        this.retMsg = retMsg;
    }

    public String getProviderId() {
        return providerId;
    }

    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }

    public String getCardIssueDate() {
        return cardIssueDate;
    }

    public void setCardIssueDate(String cardIssueDate) {
        this.cardIssueDate = cardIssueDate;
    }

    public String getMsisdn() {
        return msisdn;
    }

    public void setMsisdn(String msisdn) {
        this.msisdn = msisdn;
    }

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    public String getCardStatus() {
        return cardStatus;
    }

    public void setCardStatus(String cardStatus) {
        this.cardStatus = cardStatus;
    }

    public String getLastLineCode() {
        return lastLineCode;
    }

    public void setLastLineCode(String lastLineCode) {
        this.lastLineCode = lastLineCode;
    }

    public String getLastStationCode() {
        return lastStationCode;
    }

    public void setLastStationCode(String lastStationCode) {
        this.lastStationCode = lastStationCode;
    }

    public String getLastUpdateDate() {
        return lastUpdateDate;
    }

    public void setLastUpdateDate(String lastUpdateDate) {
        this.lastUpdateDate = lastUpdateDate;
    }

    public String getLastTransAmout() {
        return lastTransAmout;
    }

    public void setLastTransAmout(String lastTransAmout) {
        this.lastTransAmout = lastTransAmout;
    }

    public String getLastTicketTransSeq() {
        return lastTicketTransSeq;
    }

    public void setLastTicketTransSeq(String lastTicketTransSeq) {
        this.lastTicketTransSeq = lastTicketTransSeq;
    }

    public List<String> getAdviceOpt() {
        return adviceOpt;
    }

    public void setAdviceOpt(List<String> adviceOpt) {
        this.adviceOpt = adviceOpt;
    }

    public String getManagerCode() {
        return managerCode;
    }

    public void setManagerCode(String managerCode) {
        this.managerCode = managerCode;
    }

    public String getTransAmount() {
        return transAmount;
    }

    public void setTransAmount(String transAmount) {
        this.transAmount = transAmount;
    }
}
