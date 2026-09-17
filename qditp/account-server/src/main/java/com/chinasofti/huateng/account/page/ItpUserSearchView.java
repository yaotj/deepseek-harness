package com.chinasofti.huateng.account.page;

import java.time.LocalDateTime;

/**
 * USER_ITP_REG_INFO 的运营展示对象，不返回证件号和 HCE 卡数据。
 */
public class ItpUserSearchView {
    private String thirdUserId;
    private String cardId;
    private String cardType;
    private String itpCardType;
    private String msisdn;
    private String userName;
    private String cardIssueCode;
    private String channel;
    private String companionFlag;
    private String status;
    /**
     * 申卡时间（开户注册时间）。
     */
    private LocalDateTime regTms;
    /**
     * 申请解绑日期：该卡最近一次解约请求时间（APP_TERMINATION_REQUEST.REQUEST_TIME）。
     */
    private LocalDateTime terminationRequestTime;
    /**
     * 解绑成功日期：该卡最近一次解约成功时间（仅 SUCCESS 的 COMPLETE_TIME）。
     */
    private LocalDateTime terminationCompleteTime;

    public String getThirdUserId() { return thirdUserId; }
    public void setThirdUserId(String thirdUserId) { this.thirdUserId = thirdUserId; }
    public String getCardId() { return cardId; }
    public void setCardId(String cardId) { this.cardId = cardId; }
    public String getCardType() { return cardType; }
    public void setCardType(String cardType) { this.cardType = cardType; }
    public String getItpCardType() { return itpCardType; }
    public void setItpCardType(String itpCardType) { this.itpCardType = itpCardType; }
    public String getMsisdn() { return msisdn; }
    public void setMsisdn(String msisdn) { this.msisdn = msisdn; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getCardIssueCode() { return cardIssueCode; }
    public void setCardIssueCode(String cardIssueCode) { this.cardIssueCode = cardIssueCode; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getCompanionFlag() { return companionFlag; }
    public void setCompanionFlag(String companionFlag) { this.companionFlag = companionFlag; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getRegTms() { return regTms; }
    public void setRegTms(LocalDateTime regTms) { this.regTms = regTms; }
    public LocalDateTime getTerminationRequestTime() { return terminationRequestTime; }
    public void setTerminationRequestTime(LocalDateTime terminationRequestTime) { this.terminationRequestTime = terminationRequestTime; }
    public LocalDateTime getTerminationCompleteTime() { return terminationCompleteTime; }
    public void setTerminationCompleteTime(LocalDateTime terminationCompleteTime) { this.terminationCompleteTime = terminationCompleteTime; }
}
