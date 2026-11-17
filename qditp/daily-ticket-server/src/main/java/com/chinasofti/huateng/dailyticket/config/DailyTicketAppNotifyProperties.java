package com.chinasofti.huateng.dailyticket.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "daily-ticket.notify.app")
public class DailyTicketAppNotifyProperties {

    private String refundResultUrl;

    private String payResultUrl;

    private String providerId = "06";

    private String charset = "UTF-8";

    private String format = "JSON";

    private String deviceId = "";

    private String signType = "00";

    private String sign = "";

    private int connectTimeoutMs = 5000;

    private int readTimeoutMs = 10000;

    private int maxNotifyTimes = 5;

    public String getRefundResultUrl() {
        return refundResultUrl;
    }

    public void setRefundResultUrl(String refundResultUrl) {
        this.refundResultUrl = refundResultUrl;
    }

    public String getPayResultUrl() {
        return payResultUrl;
    }

    public void setPayResultUrl(String payResultUrl) {
        this.payResultUrl = payResultUrl;
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

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
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

    public int getMaxNotifyTimes() {
        return maxNotifyTimes;
    }

    public void setMaxNotifyTimes(int maxNotifyTimes) {
        this.maxNotifyTimes = maxNotifyTimes;
    }
}
