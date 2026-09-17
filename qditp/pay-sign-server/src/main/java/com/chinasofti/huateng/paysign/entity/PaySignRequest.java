package com.chinasofti.huateng.paysign.entity;

import java.time.LocalDateTime;

public class PaySignRequest {
    private Long id;
    private String requestSignSeq;
    private String thirdUserId;
    private String paymentVendor;
    private String displayAccount;
    private String operationType;
    private String requestBody;
    private String responseBody;
    private String resultCode;
    private String resultMsg;
    private String signStatus;
    private String signChannel;
    private LocalDateTime createTms;
    private String payAccountId;
    private String payAgreementNo;
    private String cardId;
    private String cardType;
    private String terminationTime;
    private String notifyStatus;
    private Integer notifyRetryCount;
    private LocalDateTime notifyTime;
    private String notifyResult;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRequestSignSeq() {
        return requestSignSeq;
    }

    public void setRequestSignSeq(String requestSignSeq) {
        this.requestSignSeq = requestSignSeq;
    }

    public String getThirdUserId() {
        return thirdUserId;
    }

    public void setThirdUserId(String thirdUserId) {
        this.thirdUserId = thirdUserId;
    }

    public String getPaymentVendor() {
        return paymentVendor;
    }

    public void setPaymentVendor(String paymentVendor) {
        this.paymentVendor = paymentVendor;
    }

    public String getDisplayAccount() {
        return displayAccount;
    }

    public void setDisplayAccount(String displayAccount) {
        this.displayAccount = displayAccount;
    }

    public String getOperationType() {
        return operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
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

    public String getSignStatus() {
        return signStatus;
    }

    public void setSignStatus(String signStatus) {
        this.signStatus = signStatus;
    }

    public String getSignChannel() {
        return signChannel;
    }

    public void setSignChannel(String signChannel) {
        this.signChannel = signChannel;
    }

    public LocalDateTime getCreateTms() {
        return createTms;
    }

    public void setCreateTms(LocalDateTime createTms) {
        this.createTms = createTms;
    }

    public String getNotifyStatus() {
        return notifyStatus;
    }

    public void setNotifyStatus(String notifyStatus) {
        this.notifyStatus = notifyStatus;
    }

    public Integer getNotifyRetryCount() {
        return notifyRetryCount;
    }

    public void setNotifyRetryCount(Integer notifyRetryCount) {
        this.notifyRetryCount = notifyRetryCount;
    }

    public LocalDateTime getNotifyTime() {
        return notifyTime;
    }

    public void setNotifyTime(LocalDateTime notifyTime) {
        this.notifyTime = notifyTime;
    }

    public String getNotifyResult() {
        return notifyResult;
    }

    public void setNotifyResult(String notifyResult) {
        this.notifyResult = notifyResult;
    }

    public String getPayAccountId() {
        return payAccountId;
    }

    public void setPayAccountId(String payAccountId) {
        this.payAccountId = payAccountId;
    }

    public String getPayAgreementNo() {
        return payAgreementNo;
    }

    public void setPayAgreementNo(String payAgreementNo) {
        this.payAgreementNo = payAgreementNo;
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

    public String getTerminationTime() {
        return terminationTime;
    }

    public void setTerminationTime(String terminationTime) {
        this.terminationTime = terminationTime;
    }
}
