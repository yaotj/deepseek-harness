package com.chinasofti.huateng.collectpay.config;

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
    private String privateKey;
    /**
     * 支付中心公钥。
     */
    private String paycenterPublicKey;
    /**
     * 支付回调地址。
     */
    private String callbackUrl;

    /**
     * tvm拉码请求地址
     */
    private String payCenterPayUrl;
    private String payCenterQueryUrl;
    private String payCenterRefundUrl;

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

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
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

    public String getPayCenterPayUrl() {
        return payCenterPayUrl;
    }

    public void setPayCenterPayUrl(String payCenterPayUrl) {
        this.payCenterPayUrl = payCenterPayUrl;
    }

    public String getPayCenterQueryUrl() {
        return payCenterQueryUrl;
    }

    public void setPayCenterQueryUrl(String payCenterQueryUrl) {
        this.payCenterQueryUrl = payCenterQueryUrl;
    }

    public String getPayCenterRefundUrl() {
        return payCenterRefundUrl;
    }

    public void setPayCenterRefundUrl(String payCenterRefundUrl) {
        this.payCenterRefundUrl = payCenterRefundUrl;
    }
}