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
     * 支付接口固定地址。
     */
    private String requestPayUrl;

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

    public String getRequestPayUrl() {
        return requestPayUrl;
    }

    public void setRequestPayUrl(String requestPayUrl) {
        this.requestPayUrl = requestPayUrl;
    }
}
