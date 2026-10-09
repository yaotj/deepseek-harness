package com.chinasofti.huateng.alipay.paysign.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 支付中心配置属性。
 */
@Component
@ConfigurationProperties(prefix = "pay.center")
public class PayCenterProperties {
    /**
     * 商户号。
     */
    private String merchantNo;
    /**
     * API版本。
     */
    private String apiVersion;
    /**
     * 签名类型。
     */
    private String signType;
    /**
     * 字符集。
     */
    private String charset;
    /**
     * 支付网关地址。
     */
    private String gatewayUrl;
    /**
     * 商户私钥。
     */
    private String merchantPrivateKey;
    /**
     * 支付中心公钥。
     */
    private String paycenterPublicKey;
    /**
     * 支付回调地址。
     */
    private String callbackUrl;
    /**
     * 退款结果回调地址，对应契约 §3.1 请求退款 bizData 的必填键 {@code notifyUrl}。
     *
     * <p>空值时 {@code notifyUrl} 不送、并打 WARN：支付中心只往「本次请求带的 notifyUrl」推退款结果，
     * 这个键不配等于**永远收不到退款回调**，退款明细只能靠退款回查补偿收口。
     * 值 MUST 是支付中心侧网络可达的我方地址，形态参照 {@code callbackUrl}。
     */
    private String refundNotifyUrl;
    /**
     * 支付接口固定地址。
     */
    private String requestPayUrl;
    /**
     * 退款接口固定地址。
     */
    private String requestRefundUrl;
    /**
     * 支付查询接口固定地址。
     */
    private String payQueryUrl;
    /**
     * 退款查询接口固定地址。
     */
    private String refundQueryUrl;
    /**
     * 业务关闭结果通知固定地址。
     */
    private String closeResultNotifyUrl;
    /**
     * 黑名单变更通知固定地址。
     */
    private String blacklistNotifyUrl;

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

    public String getGatewayUrl() {
        return gatewayUrl;
    }

    public void setGatewayUrl(String gatewayUrl) {
        this.gatewayUrl = gatewayUrl;
    }

    public String getMerchantPrivateKey() {
        return merchantPrivateKey;
    }

    public void setMerchantPrivateKey(String merchantPrivateKey) {
        this.merchantPrivateKey = merchantPrivateKey;
    }

    public String getPaycenterPublicKey() {
        return paycenterPublicKey;
    }

    public void setPaycenterPublicKey(String paycenterPublicKey) {
        this.paycenterPublicKey = paycenterPublicKey;
    }

    public String getCallbackUrl() {
        return callbackUrl;
    }

    public void setCallbackUrl(String callbackUrl) {
        this.callbackUrl = callbackUrl;
    }

    public String getRefundNotifyUrl() {
        return refundNotifyUrl;
    }

    public void setRefundNotifyUrl(String refundNotifyUrl) {
        this.refundNotifyUrl = refundNotifyUrl;
    }

    public String getRequestPayUrl() {
        return requestPayUrl;
    }

    public void setRequestPayUrl(String requestPayUrl) {
        this.requestPayUrl = requestPayUrl;
    }

    public String getRequestRefundUrl() {
        return requestRefundUrl;
    }

    public void setRequestRefundUrl(String requestRefundUrl) {
        this.requestRefundUrl = requestRefundUrl;
    }

    public String getPayQueryUrl() {
        return payQueryUrl;
    }

    public void setPayQueryUrl(String payQueryUrl) {
        this.payQueryUrl = payQueryUrl;
    }

    public String getRefundQueryUrl() {
        return refundQueryUrl;
    }

    public void setRefundQueryUrl(String refundQueryUrl) {
        this.refundQueryUrl = refundQueryUrl;
    }

    public String getCloseResultNotifyUrl() {
        return closeResultNotifyUrl;
    }

    public void setCloseResultNotifyUrl(String closeResultNotifyUrl) {
        this.closeResultNotifyUrl = closeResultNotifyUrl;
    }

    public String getBlacklistNotifyUrl() {
        return blacklistNotifyUrl;
    }

    public void setBlacklistNotifyUrl(String blacklistNotifyUrl) {
        this.blacklistNotifyUrl = blacklistNotifyUrl;
    }
}
