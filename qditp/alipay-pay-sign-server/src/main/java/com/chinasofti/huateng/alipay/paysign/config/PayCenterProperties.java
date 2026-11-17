package com.chinasofti.huateng.alipay.paysign.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;


@Component
@ConfigurationProperties(prefix = "pay.center")
public class PayCenterProperties {

    private String merchantNo;

    private String apiVersion;

    private String signType;

    private String charset;

    private String gatewayUrl;

    private String merchantPrivateKey;

    private String paycenterPublicKey;

    private String callbackUrl;

    private String refundNotifyUrl;

    private String requestPayUrl;

    private String requestRefundUrl;

    private String payQueryUrl;

    private String refundQueryUrl;

    private String closeResultNotifyUrl;

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
