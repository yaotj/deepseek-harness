package com.chinasofti.huateng.dailyticket.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "daily-ticket.pay")
public class DailyTicketPayProperties {
    private String gatewayUrl;
    private String merchantNo = "JOPJ490HLK9Z";
    private String apiVersion = "1.0";
    private String signType = "RSA2";
    private String charset = "UTF-8";
    private String merchantPrivateKey;
    private String requestPayPath = "/api/payment/requestPay";
    private String requestRefundPath = "/api/refund/requestRefund";
    private String payQueryPath = "/api/payment/payQuery";
    private String refundQueryPath = "/api/refund/refundQuery";
    private String notifyUrl;
    private String refundNotifyUrl;
    private String returnUrl;
    private String industryType = "1";
    private String subject = "虚拟电子票";
    private String body = "虚拟电子票";

    public String getGatewayUrl() {
        return gatewayUrl;
    }

    public void setGatewayUrl(String gatewayUrl) {
        this.gatewayUrl = gatewayUrl;
    }

    public String getMerchantNo() {
        return merchantNo;
    }

    public void setMerchantNo(String merchantNo) {
        this.merchantNo = merchantNo;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public String getSignType() {
        return signType;
    }

    public void setSignType(String signType) {
        this.signType = signType;
    }

    public String getCharset() {
        return charset;
    }

    public void setCharset(String charset) {
        this.charset = charset;
    }

    public String getMerchantPrivateKey() {
        return merchantPrivateKey;
    }

    public void setMerchantPrivateKey(String merchantPrivateKey) {
        this.merchantPrivateKey = merchantPrivateKey;
    }

    public String getRequestPayPath() {
        return requestPayPath;
    }

    public void setRequestPayPath(String requestPayPath) {
        this.requestPayPath = requestPayPath;
    }

    public String getRequestRefundPath() {
        return requestRefundPath;
    }

    public void setRequestRefundPath(String requestRefundPath) {
        this.requestRefundPath = requestRefundPath;
    }

    public String getPayQueryPath() {
        return payQueryPath;
    }

    public void setPayQueryPath(String payQueryPath) {
        this.payQueryPath = payQueryPath;
    }

    public String getRefundQueryPath() {
        return refundQueryPath;
    }

    public void setRefundQueryPath(String refundQueryPath) {
        this.refundQueryPath = refundQueryPath;
    }

    public String getNotifyUrl() {
        return notifyUrl;
    }

    public void setNotifyUrl(String notifyUrl) {
        this.notifyUrl = notifyUrl;
    }

    public String getRefundNotifyUrl() {
        return refundNotifyUrl;
    }

    public void setRefundNotifyUrl(String refundNotifyUrl) {
        this.refundNotifyUrl = refundNotifyUrl;
    }

    public String getReturnUrl() {
        return returnUrl;
    }

    public void setReturnUrl(String returnUrl) {
        this.returnUrl = returnUrl;
    }

    public String getIndustryType() {
        return industryType;
    }

    public void setIndustryType(String industryType) {
        this.industryType = industryType;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }
}
