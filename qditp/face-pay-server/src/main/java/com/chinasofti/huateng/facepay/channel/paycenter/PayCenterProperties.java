package com.chinasofti.huateng.facepay.channel.paycenter;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pay.center")
public class PayCenterProperties {

    private String apiVersion = "1.0";

    private String charset = "UTF-8";

    private String signType = "RSA";

    private String merchantNo;

    private String privateKey;

    private String signAlgorithm = "SHA256WithRSA";

    private String jhmKey;

    private String payUrl;

    private String queryUrl;

    private String refundUrl;

    private String refundQueryUrl;

    private String checkoutCounterUrl;

    private String payNoticeUrl;

    private String supplementNoticeUrl;

    private String refundNoticeUrl;

    private long orderTimeOutSeconds = 180L;

    private String industryType = "1";

    private String refundReason;

    private int connectTimeoutMs = 10000;

    private int readTimeoutMs = 30000;

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public String getCharset() {
        return charset;
    }

    public void setCharset(String charset) {
        this.charset = charset;
    }

    public String getSignType() {
        return signType;
    }

    public void setSignType(String signType) {
        this.signType = signType;
    }

    public String getMerchantNo() {
        return merchantNo;
    }

    public void setMerchantNo(String merchantNo) {
        this.merchantNo = merchantNo;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }

    public String getSignAlgorithm() {
        return signAlgorithm;
    }

    public void setSignAlgorithm(String signAlgorithm) {
        this.signAlgorithm = signAlgorithm;
    }

    public String getJhmKey() {
        return jhmKey;
    }

    public void setJhmKey(String jhmKey) {
        this.jhmKey = jhmKey;
    }

    public String getPayUrl() {
        return payUrl;
    }

    public void setPayUrl(String payUrl) {
        this.payUrl = payUrl;
    }
    public String getQueryUrl() {
        return queryUrl;
    }

    public void setQueryUrl(String queryUrl) {
        this.queryUrl = queryUrl;
    }

    public String getRefundUrl() {
        return refundUrl;
    }

    public void setRefundUrl(String refundUrl) {
        this.refundUrl = refundUrl;
    }

    public String getRefundQueryUrl() {
        return refundQueryUrl;
    }

    public void setRefundQueryUrl(String refundQueryUrl) {
        this.refundQueryUrl = refundQueryUrl;
    }

    public String getCheckoutCounterUrl() {
        return checkoutCounterUrl;
    }

    public void setCheckoutCounterUrl(String checkoutCounterUrl) {
        this.checkoutCounterUrl = checkoutCounterUrl;
    }

    public String getPayNoticeUrl() {
        return payNoticeUrl;
    }

    public void setPayNoticeUrl(String payNoticeUrl) {
        this.payNoticeUrl = payNoticeUrl;
    }

    public String getSupplementNoticeUrl() {
        return supplementNoticeUrl;
    }

    public void setSupplementNoticeUrl(String supplementNoticeUrl) {
        this.supplementNoticeUrl = supplementNoticeUrl;
    }

    public String effectiveSupplementNoticeUrl() {
        return supplementNoticeUrl == null || supplementNoticeUrl.isBlank()
                ? payNoticeUrl : supplementNoticeUrl;
    }

    public String getRefundNoticeUrl() {
        return refundNoticeUrl;
    }

    public void setRefundNoticeUrl(String refundNoticeUrl) {
        this.refundNoticeUrl = refundNoticeUrl;
    }

    public long getOrderTimeOutSeconds() {
        return orderTimeOutSeconds;
    }

    public void setOrderTimeOutSeconds(long orderTimeOutSeconds) {
        this.orderTimeOutSeconds = orderTimeOutSeconds;
    }

    public String getIndustryType() {
        return industryType;
    }

    public void setIndustryType(String industryType) {
        this.industryType = industryType;
    }

    public String getRefundReason() {
        return refundReason;
    }

    public void setRefundReason(String refundReason) {
        this.refundReason = refundReason;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }
}
