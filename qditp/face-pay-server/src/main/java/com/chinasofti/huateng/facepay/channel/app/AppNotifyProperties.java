package com.chinasofti.huateng.facepay.channel.app;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** APP 网关通知地址（IF8B-04/05/06/07 出向）。 */
@ConfigurationProperties(prefix = "f2f.notify.app")
public class AppNotifyProperties {

    /** 出票成功通知，对应旧 {@code pay.center.notice-app-taketicketresult-url}。 */
    private String takeTicketOkUrl;

    /** 出票失败通知，对应旧 {@code pay.center.notice-app-taketicketfailureresult-url}。 */
    private String takeTicketFailUrl;

    /** 退款结果通知，对应旧 {@code pay.center.notice-app-refundresult-url}。 */
    private String refundResultUrl;

    /** APP 退款受理响应里回吐给 APP 的 {@code notifyUrl}，对应旧 {@code pay.center.app-refund-notice-url}（集群 env 实测指向 fep-app 的 {@code /ci/app/receiveRefundResult}，仓库 yml 里那条 payNotice 是过期值）。 */
    private String refundNoticeUrl;

    /** 支付结果通知。 */
    private String payResultUrl;

    /** ITP 报文信封的 {@code providerId}，默认 {@code 06}。 */
    private String providerId = "06";

    /** 信封 {@code charset}。 */
    private String charset = "UTF-8";

    /** 信封 {@code format}。 */
    private String format = "json";

    /** 信封 {@code signType}，默认 {@code 00}（免签）。 */
    private String signType = "00";

    /** 信封 {@code sign}，免签时为空串。 */
    private String sign = "";

    /** 信封 {@code deviceId}，出向通知无设备概念，旧实现固定空串。 */
    private String deviceId = "";

    /** 连接超时，毫秒。 */
    private int connectTimeoutMs = 3000;

    /** 读超时，毫秒。 */
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
