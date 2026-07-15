package com.chinasofti.huateng.dailyticket.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 日票支付网关配置。
 */
@ConfigurationProperties(prefix = "daily-ticket.pay")
public class DailyTicketPayProperties {
    /** 支付网关地址。 */
    private String gatewayUrl;
    /** 商户号。 */
    private String merchantNo = "M100001";
    /** API版本。 */
    private String apiVersion = "1.0";
    /** 签名类型。 */
    private String signType = "RSA2";
    /** 字符集。 */
    private String charset = "UTF-8";
    /** 商户RSA私钥，Base64编码PKCS8格式。 */
    private String merchantPrivateKey;
    /** 请求支付路径。 */
    private String requestPayPath = "/api/payment/requestPay";
    /** 请求退款路径。 */
    private String requestRefundPath = "/api/refund/requestRefund";
    /** 支付结果通知地址。 */
    private String notifyUrl;
    /** 支付完成返回地址。 */
    private String returnUrl;
    /** 日票支付行业类型。 */
    private String industryType = "dailyTicket";
    /** 日票支付标题。 */
    private String subject = "虚拟电子票";
    /** 日票支付描述。 */
    private String body = "虚拟电子票";

    public String getGatewayUrl() {
        return gatewayUrl;
    }

    public void setGatewayUrl(String gatewayUrl) {
        this.gatewayUrl = gatewayUrl;
    }

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

    public String getMerchantPrivateKey() {
        return merchantPrivateKey;
    }

    public void setMerchantPrivateKey(String merchantPrivateKey) {
        this.merchantPrivateKey = merchantPrivateKey;
    }

    public String getRequestPayPath() {
        return requestPayPath;
    }

    public void setRequestPayPath(String requestPayPath) {
        this.requestPayPath = requestPayPath;
    }

    public String getRequestRefundPath() {
        return requestRefundPath;
    }

    public void setRequestRefundPath(String requestRefundPath) {
        this.requestRefundPath = requestRefundPath;
    }

    public String getNotifyUrl() {
        return notifyUrl;
    }

    public void setNotifyUrl(String notifyUrl) {
        this.notifyUrl = notifyUrl;
    }

    public String getReturnUrl() {
        return returnUrl;
    }

    public void setReturnUrl(String returnUrl) {
        this.returnUrl = returnUrl;
    }

    public String getIndustryType() {
        return industryType;
    }

    public void setIndustryType(String industryType) {
        this.industryType = industryType;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }
}
