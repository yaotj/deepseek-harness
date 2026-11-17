package com.chinasofti.huateng.model.app;


public class RequestSignInfoReqDTO {


    private String thirdUserId;


    private String certNo;


    private String notifyUrl;


    private String returnUrl;


    private String options;


    private String authCode;


    private String mobilePhone;


    private String payChannelCode;


    private String requestSignSeq;


    private String displayAccount;


    private String token;


    private String payUserId;


    private String bankCardNo;


    private String custName;

    private String other;

    public String getThirdUserId() {
        return thirdUserId;
    }

    public void setThirdUserId(String thirdUserId) {
        this.thirdUserId = thirdUserId;
    }

    public String getCertNo() {
        return certNo;
    }

    public void setCertNo(String certNo) {
        this.certNo = certNo;
    }

    public String getNotifyUrl() {
        return notifyUrl;
    }

    public void setNotifyUrl(String notifyUrl) {
        this.notifyUrl = notifyUrl;
    }

    public String getReturnUrl() {
        return returnUrl;
    }

    public void setReturnUrl(String returnUrl) {
        this.returnUrl = returnUrl;
    }

    public String getOptions() {
        return options != null ? options : other;
    }

    public void setOptions(String options) {
        this.options = options;
    }

    public String getAuthCode() {
        return authCode;
    }

    public void setAuthCode(String authCode) {
        this.authCode = authCode;
    }

    public String getMobilePhone() {
        return mobilePhone;
    }

    public void setMobilePhone(String mobilePhone) {
        this.mobilePhone = mobilePhone;
    }

    public String getPayChannelCode() {
        return payChannelCode;
    }

    public void setPayChannelCode(String payChannelCode) {
        this.payChannelCode = payChannelCode;
    }

    public String getRequestSignSeq() {
        return requestSignSeq;
    }

    public void setRequestSignSeq(String requestSignSeq) {
        this.requestSignSeq = requestSignSeq;
    }

    public String getDisplayAccount() {
        return displayAccount;
    }

    public void setDisplayAccount(String displayAccount) {
        this.displayAccount = displayAccount;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getPayUserId() {
        return payUserId;
    }

    public void setPayUserId(String payUserId) {
        this.payUserId = payUserId;
    }

    public String getBankCardNo() {
        return bankCardNo;
    }

    public void setBankCardNo(String bankCardNo) {
        this.bankCardNo = bankCardNo;
    }

    public String getCustName() {
        return custName;
    }

    public void setCustName(String custName) {
        this.custName = custName;
    }

    public String getOther() {
        return other;
    }

    public void setOther(String other) {
        this.other = other;
    }

    @Override
    public String toString() {
        return "RequestSignInfoReqDTO{" +
                "thirdUserId='" + thirdUserId + '\'' +
                ", requestSignSeq='" + requestSignSeq + '\'' +
                ", displayAccount='" + displayAccount + '\'' +
                '}';
    }
}
