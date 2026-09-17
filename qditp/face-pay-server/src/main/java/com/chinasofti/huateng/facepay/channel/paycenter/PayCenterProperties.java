package com.chinasofti.huateng.facepay.channel.paycenter;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 支付中心对接配置，前缀 {@code pay.center}。 */
@ConfigurationProperties(prefix = "pay.center")
public class PayCenterProperties {

    /** 报文公共参数 apiVersion。 */
    private String apiVersion = "1.0";

    /** 报文公共参数 charset。 */
    private String charset = "UTF-8";

    /** 报文公共参数 signType，旧实现取值 RSA。 */
    private String signType = "RSA";

    /** 商户号。 */
    private String merchantNo;

    /** 商户私钥，Base64 编码的 PKCS#8。 */
    private String privateKey;

    /** 签名算法，旧实现由 sign.algorithm 提供，取值 SHA256WithRSA。 */
    private String signAlgorithm = "SHA256WithRSA";

    /** 聚合码签名用的 MD5 拼接密钥，旧实现由 jhm.key 提供。 */
    private String jhmKey;

    /** 预下单完整 URL。 */
    private String payUrl;

    /** 支付结果查询完整 URL。 */
    private String queryUrl;

    /** 退款完整 URL。 */
    private String refundUrl;

    /** 退款结果查询完整 URL。 */
    private String refundQueryUrl;

    /** 聚合码收银台完整 URL，payType=0 时本地拼串返给设备，不调支付中心。 */
    private String checkoutCounterUrl;

    /** 支付结果回调地址，作为 bizData.notifyUrl 发给支付中心。 */
    private String payNoticeUrl;

    /** 补款单（IF8A-26，{@code SP} 前缀）专用的支付结果回调地址。 */
    private String supplementNoticeUrl;

    /** 订单超时秒数，旧实现固定 180。 */
    private long orderTimeOutSeconds = 180L;

    /** 行业类型，旧实现固定 1（地铁）。 */
    private String industryType = "1";

    /** 退款原因，旧实现取自 pay.center.refundReason。 */
    private String refundReason;

    /** HTTP 连接超时毫秒。 */
    private int connectTimeoutMs = 10000;

    /** HTTP 读超时毫秒。 */
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

    /** 补款单实际使用的回调地址：配了就用补款专用的，没配回落到通用的。 */
    public String effectiveSupplementNoticeUrl() {
        return supplementNoticeUrl == null || supplementNoticeUrl.isBlank()
                ? payNoticeUrl : supplementNoticeUrl;
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
