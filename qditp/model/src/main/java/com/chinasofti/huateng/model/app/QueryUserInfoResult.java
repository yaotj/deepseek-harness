package com.chinasofti.huateng.model.app;

import com.chinasofti.huateng.common.response.CommonResult;

public class QueryUserInfoResult extends CommonResult {
    private String thirdUserId;
    private String cardId;
    private String cardType;
    private String itpCardType;
    private String channel;
    private String cardIssueCode;
    private String thirdPayId;
    private String reqContractNo;
    /**
     * HCE 卡当前卡数据。仅 HCE 卡类型 {@code 0442}/{@code 0443} 有值。
     */
    private String hceData;

    /**
     * 手机号。
     */
    private String msisdn;

    /**
     * 注册时间。
     */
    private String regTms;

    /**
     * 同行票或第三方票标识。
     */
    private String companionFlag;

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

    public String getItpCardType() {
        return itpCardType;
    }

    public void setItpCardType(String itpCardType) {
        this.itpCardType = itpCardType;
    }


    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getCardIssueCode() {
        return cardIssueCode;
    }

    public void setCardIssueCode(String cardIssueCode) {
        this.cardIssueCode = cardIssueCode;
    }

    public String getThirdPayId() {
        return thirdPayId;
    }

    public void setThirdPayId(String thirdPayId) {
        this.thirdPayId = thirdPayId;
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

    public String getMsisdn() {
        return msisdn;
    }

    public void setMsisdn(String msisdn) {
        this.msisdn = msisdn;
    }

    public String getRegTms() {
        return regTms;
    }

    public void setRegTms(String regTms) {
        this.regTms = regTms;
    }

    public String getCompanionFlag() {
        return companionFlag;
    }

    public void setCompanionFlag(String companionFlag) {
        this.companionFlag = companionFlag;
    }
}
