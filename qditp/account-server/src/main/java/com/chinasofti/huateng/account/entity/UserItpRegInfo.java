package com.chinasofti.huateng.account.entity;

import java.time.LocalDateTime;

/**
 * User_ITP_Reg_Info 用户注册表实体。
 */
public class UserItpRegInfo {
    /**
     * 主键。
     */
    private Integer id;

    /**
     * 逻辑卡号。
     */
    private String cardId;

    /**
     * 卡类型。
     */
    private String cardType;

    /**
     * 第三方用户编码。
     */
    private String thirdUserId;

    /**
     * 手机号。
     */
    private String msisdn;

    /**
     * 注册时间。
     */
    private LocalDateTime regTms;

    /**
     * 删除标志。
     */
    private Integer delYn;

    /**
     * 删除操作对应的第三方用户编码。
     */
    private String delThirdUserId;

    /**
     * 注销时间。
     */
    private LocalDateTime unRegTms;

    /**
     * 用户姓名。
     */
    private String userName;

    /**
     * 证件号。
     */
    private String userId;

    /**
     * 发卡机构编码。
     */
    private String cardIssueCode;

    /**
     * 第三方支付渠道用户标识。
     */
    private String thirdPayId;

    /**
     * 渠道编码。
     */
    private String channel;

    /**
     * 签约请求号。
     */
    private String reqContractNo;

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
}
