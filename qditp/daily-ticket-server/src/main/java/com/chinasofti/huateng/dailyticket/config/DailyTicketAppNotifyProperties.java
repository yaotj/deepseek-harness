package com.chinasofti.huateng.dailyticket.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 日票/旅游票通知 APP 的出向配置。 */
@ConfigurationProperties(prefix = "daily-ticket.notify.app")
public class DailyTicketAppNotifyProperties {

    /** IF8B-04 退款结果通知地址（完整 URL），路径形如 {@code /ci/app/v2/receiveRefundResult}。 */
    private String refundResultUrl;

    /** IF8B-05 支付结果通知地址（完整 URL），路径形如 {@code /app/receivePaymentResult}。 */
    private String payResultUrl;

    /** ITP 信封 {@code providerId}。 */
    private String providerId = "06";

    /** ITP 信封 {@code charset}。 */
    private String charset = "UTF-8";

    /** ITP 信封 {@code format}。 */
    private String format = "JSON";

    /** ITP 信封 {@code deviceId}，出向通知无设备概念，默认空串。 */
    private String deviceId = "";

    /** ITP 信封 {@code signType}，默认 {@code 00} 免签。 */
    private String signType = "00";

    /** ITP 信封 {@code sign}，免签时为空串。 */
    private String sign = "";

    /** 连接超时，毫秒。 */
    private int connectTimeoutMs = 5000;

    /** 读超时，毫秒。通知是扫表补偿驱动的异步任务，可比设备链路给得宽松些。 */
    private int readTimeoutMs = 10000;

    /** 最大投递次数，达到即置 {@code GIVEUP} 不再重投。 */
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
