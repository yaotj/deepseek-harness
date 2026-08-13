package com.chinasofti.huateng.model.ticket;

import java.util.List;

/**
 * 请求票卡分析响应DTO。
 */
public class RequestCardDataAnalyseRespDTO {

    /**
     * 返回码。
     */
    private String retCode;

    /**
     * 返回消息。
     */
    private String retMsg;

    /**
     * 发行方代码。
     */
    private String providerId;

    /**
     * 发票日期。
     */
    private String cardIssueDate;

    /**
     * 手机号。
     */
    private String msisdn;

    /**
     * 逻辑卡号。
     */
    private String cardId;

    /**
     * 票卡状态。
     */
    private String cardStatus;

    /**
     * 上一次线路代码。
     */
    private String lastLineCode;

    /**
     * 上一次车站代码。
     */
    private String lastStationCode;

    /**
     * 上一次交易时间。
     */
    private String lastUpdateDate;

    /**
     * 上次交易金额。
     */
    private String lastTransAmout;

    /**
     * 上次里程序列号。
     */
    private String lastTicketTransSeq;

    /**
     * 建议本次操作类型。
     */
    private List<String> adviceOpt;

    /**
     * 操作员编码。
     */
    private String managerCode;

    /**
     * 20分付费更新 金额。
     */
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
