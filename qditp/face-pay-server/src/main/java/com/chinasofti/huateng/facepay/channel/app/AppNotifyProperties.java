package com.chinasofti.huateng.facepay.channel.app;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "f2f.notify.app")
public class AppNotifyProperties {

    private String takeTicketOkUrl;

    private String takeTicketFailUrl;

    private String refundResultUrl;

    private String refundNoticeUrl;

    private String payResultUrl;

    private String providerId = "06";

    private String charset = "UTF-8";

    private String format = "json";

    private String signType = "00";

    private String sign = "";

    private String deviceId = "";

    private int connectTimeoutMs = 3000;

    private int readTimeoutMs = 10000;

    public String getTakeTicketOkUrl() {
        return takeTicketOkUrl;
    }

    public void setTakeTicketOkUrl(String takeTicketOkUrl) {
        this.takeTicketOkUrl = takeTicketOkUrl;
    }

    public String getTakeTicketFailUrl() {
        return takeTicketFailUrl;
    }

    public void setTakeTicketFailUrl(String takeTicketFailUrl) {
        this.takeTicketFailUrl = takeTicketFailUrl;
    }

    public String getRefundResultUrl() {
        return refundResultUrl;
    }

    public void setRefundResultUrl(String refundResultUrl) {
        this.refundResultUrl = refundResultUrl;
    }

    public String getRefundNoticeUrl() {
        return refundNoticeUrl;
    }

    public void setRefundNoticeUrl(String refundNoticeUrl) {
        this.refundNoticeUrl = refundNoticeUrl;
    }

    public String getPayResultUrl() {
        return payResultUrl;
    }

    public void setPayResultUrl(String payResultUrl) {
        this.payResultUrl = payResultUrl;
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

    public String getProviderId() {
        return providerId;
    }

    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }

    public String getCharset() {
        return charset;
    }

    public void setCharset(String charset) {
        this.charset = charset;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getSignType() {
        return signType;
    }

    public void setSignType(String signType) {
        this.signType = signType;
    }

    public String getSign() {
        return sign;
    }

    public void setSign(String sign) {
        this.sign = sign;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }
}
