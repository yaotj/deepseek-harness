package com.chinasofti.huateng.alipay.account.entity;

import java.time.LocalDateTime;

/**
 * ALIPAY_REG_LOG 支付宝开户流水表实体。
 */
public class AlipayRegLog {

    /**
     * 请求流水号（主键）。
     */
    private String requestSeq;

    /**
     * 响应流水号。
     */
    private String responseSeq;

    /**
     * 支付宝用户ID。
     */
    private String thirdUserId;

    /**
     * 逻辑卡号。
     */
    private String cardId;

    /**
     * 卡类型编码。
     */
    private String cardType;

    /**
     * 发卡渠道代码。
     */
    private String cardIssueCode;

    /**
     * 开户渠道编码。
     */
    private String channel;

    /**
     * 手机号。
     */
    private String msisdn;

    /**
     * 扩展字段1。
     */
    private String extend1;

    /**
     * 扩展字段2。
     */
    private String extend2;

    /**
     * 原始请求报文。
     */
    private String requestBody;

    /**
     * 原始响应报文。
     */
    private String responseBody;

    /**
     * 响应码。
     */
    private String resultCode;

    /**
     * 响应信息。
     */
    private String resultMsg;

    /**
     * 操作人。
     */
    private String operator;

    /**
     * 请求IP。
     */
    private String ip;

    /**
     * 备注。
     */
    private String remark;

    /**
     * 乐观锁版本号。
     */
    private String version;

    /**
     * 创建时间。
     */
    private LocalDateTime createTime;

    /**
     * 创建人。
     */
    private String createBy;

    public String getRequestSeq() {
        return requestSeq;
    }

    public void setRequestSeq(String requestSeq) {
        this.requestSeq = requestSeq;
    }

    public String getResponseSeq() {
        return responseSeq;
    }

    public void setResponseSeq(String responseSeq) {
        this.responseSeq = responseSeq;
    }

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

    public String getCardIssueCode() {
        return cardIssueCode;
    }

    public void setCardIssueCode(String cardIssueCode) {
        this.cardIssueCode = cardIssueCode;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getMsisdn() {
        return msisdn;
    }

    public void setMsisdn(String msisdn) {
        this.msisdn = msisdn;
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

    public String getRequestBody() {
        return requestBody;
    }

    public void setRequestBody(String requestBody) {
        this.requestBody = requestBody;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }

    public String getResultCode() {
        return resultCode;
    }

    public void setResultCode(String resultCode) {
        this.resultCode = resultCode;
    }

    public String getResultMsg() {
        return resultMsg;
    }

    public void setResultMsg(String resultMsg) {
        this.resultMsg = resultMsg;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }
}
