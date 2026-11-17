package com.chinasofti.huateng.dailyticket.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "daily-ticket.acc.active-notify")
public class DailyTicketAccNotifyProperties {

    private String url;

    private String providerId = "06";

    private String charset = "UTF-8";

    private String format = "json";

    private String deviceId = "ITP-DAILY-TICKET";

    private String signType = "00";

    private String sign = "";

    private int maxNotifyTimes = 5;

    private int defaultLimit = 100;

    private int connectTimeoutMs = 5000;

    private int readTimeoutMs = 10000;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
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

    public int getMaxNotifyTimes() {
        return maxNotifyTimes;
    }

    public void setMaxNotifyTimes(int maxNotifyTimes) {
        this.maxNotifyTimes = maxNotifyTimes;
    }

    public int getDefaultLimit() {
        return defaultLimit;
    }

    public void setDefaultLimit(int defaultLimit) {
        this.defaultLimit = defaultLimit;
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
