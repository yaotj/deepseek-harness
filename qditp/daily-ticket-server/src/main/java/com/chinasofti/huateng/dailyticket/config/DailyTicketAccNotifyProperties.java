package com.chinasofti.huateng.dailyticket.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 日票激活后通知 ACC 发售的出向配置。 */
@ConfigurationProperties(prefix = "daily-ticket.acc.active-notify")
public class DailyTicketAccNotifyProperties {

    /** ACC 发售通知完整地址。 */
    private String url;

    /** ITP 公共请求 providerId。 */
    private String providerId = "06";

    /** ITP 公共请求 charset。 */
    private String charset = "UTF-8";

    /** ITP 公共请求 format。 */
    private String format = "json";

    /** ITP 公共请求 deviceId。 */
    private String deviceId = "ITP-DAILY-TICKET";

    /** ITP 公共请求 signType。 */
    private String signType = "00";

    /** ITP 公共请求 sign。 */
    private String sign = "";

    /** 最大通知次数，达到即置 GIVEUP。 */
    private int maxNotifyTimes = 5;

    /** 单批默认补偿条数。 */
    private int defaultLimit = 100;

    /** 连接超时，毫秒。 */
    private int connectTimeoutMs = 5000;

    /** 读超时，毫秒。 */
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
