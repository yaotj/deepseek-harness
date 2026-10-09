package com.chinasofti.huateng.paysign.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pay.sign")
public class PaySignProperties {
    private String apiVersion = "1.0";
    private String signType = "RSA2";
    private String charset = "UTF-8";
    private String merchantNo = "M100001";
    private String merchantPrivateKey;
    /** 支付中心各接口的完整 URL，与 service.*.url、app.notify.*-url 保持一致的配置形态。 */
    private String contractConfigUrl;
    private String contractUrl;
    private String contractAdvisoryUrl;
    private String contractResultUrl;
    private String terminationUrl;
    private String requestPayUrl;
    /** 支付查询（网关文档 §1.2 payQuery），只读接口，用于在拉黑前二次确认支付中心侧的真实状态。 */
    private String payQueryUrl;
    private String requestRefundUrl;
    /** 退款查询（网关文档 §3.2 refundQuery，`docs/external/支付中心网关接口文档.md:342~368`），只读接口。 */
    private String refundQueryUrl;
    private String defaultNotifyUrl;
    private String requestPayNotifyUrl;
    /**
     * §3.1 请求退款的回调地址（{@code pay.sign.request-refund-notify-url}，2026-09-22 新增，P1-3）。
     *
     * <p>与 {@link #requestPayNotifyUrl}（§5.1 支付回调）、{@link #defaultNotifyUrl}（签约回调）
     * **是三条不同的回调、报文与处理分支都不同，NEVER 互相顶用**。
     * 空值即「不送该键」，退款仍只靠 §3.2 回查收敛。
     */
    private String requestRefundNotifyUrl;
    private String alipayAppId = "60000157";
    private String alipayMerchantAppId = "2015101000413186";
    private String wechatAppId = "wx426a3015555a46be";
    private String wechatEntrustUrl = "https://api.mch.weixin.qq.com/papay/entrustweb";
    /** testForceAmount（配置键 pay.sign.test-force-amount）已于 2026-09-16 删除。 */

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

    public String getMerchantNo() {
        return merchantNo;
    }

    public void setMerchantNo(String merchantNo) {
        this.merchantNo = merchantNo;
    }

    public String getMerchantPrivateKey() {
        return merchantPrivateKey;
    }

    public void setMerchantPrivateKey(String merchantPrivateKey) {
        this.merchantPrivateKey = merchantPrivateKey;
    }

    public String getContractConfigUrl() {
        return contractConfigUrl;
    }

    public void setContractConfigUrl(String contractConfigUrl) {
        this.contractConfigUrl = contractConfigUrl;
    }

    public String getContractUrl() {
        return contractUrl;
    }

    public void setContractUrl(String contractUrl) {
        this.contractUrl = contractUrl;
    }

    public String getContractAdvisoryUrl() {
        return contractAdvisoryUrl;
    }

    public void setContractAdvisoryUrl(String contractAdvisoryUrl) {
        this.contractAdvisoryUrl = contractAdvisoryUrl;
    }

    public String getContractResultUrl() {
        return contractResultUrl;
    }

    public void setContractResultUrl(String contractResultUrl) {
        this.contractResultUrl = contractResultUrl;
    }

    public String getTerminationUrl() {
        return terminationUrl;
    }

    public void setTerminationUrl(String terminationUrl) {
        this.terminationUrl = terminationUrl;
    }

    public String getRequestPayUrl() {
        return requestPayUrl;
    }

    public void setRequestPayUrl(String requestPayUrl) {
        this.requestPayUrl = requestPayUrl;
    }

    public String getPayQueryUrl() {
        return payQueryUrl;
    }

    public void setPayQueryUrl(String payQueryUrl) {
        this.payQueryUrl = payQueryUrl;
    }

    public String getRequestRefundUrl() {
        return requestRefundUrl;
    }

    public void setRequestRefundUrl(String requestRefundUrl) {
        this.requestRefundUrl = requestRefundUrl;
    }

    public String getRefundQueryUrl() {
        return refundQueryUrl;
    }

    public void setRefundQueryUrl(String refundQueryUrl) {
        this.refundQueryUrl = refundQueryUrl;
    }

    public String getDefaultNotifyUrl() {
        return defaultNotifyUrl;
    }

    public void setDefaultNotifyUrl(String defaultNotifyUrl) {
        this.defaultNotifyUrl = defaultNotifyUrl;
    }

    public String getRequestPayNotifyUrl() {
        return requestPayNotifyUrl;
    }

    public void setRequestPayNotifyUrl(String requestPayNotifyUrl) {
        this.requestPayNotifyUrl = requestPayNotifyUrl;
    }

    public String getRequestRefundNotifyUrl() {
        return requestRefundNotifyUrl;
    }

    public void setRequestRefundNotifyUrl(String requestRefundNotifyUrl) {
        this.requestRefundNotifyUrl = requestRefundNotifyUrl;
    }

    public String getAlipayAppId() {
        return alipayAppId;
    }

    public void setAlipayAppId(String alipayAppId) {
        this.alipayAppId = alipayAppId;
    }

    public String getAlipayMerchantAppId() {
        return alipayMerchantAppId;
    }

    public void setAlipayMerchantAppId(String alipayMerchantAppId) {
        this.alipayMerchantAppId = alipayMerchantAppId;
    }

    public String getWechatAppId() {
        return wechatAppId;
    }

    public void setWechatAppId(String wechatAppId) {
        this.wechatAppId = wechatAppId;
    }

    public String getWechatEntrustUrl() {
        return wechatEntrustUrl;
    }

    public void setWechatEntrustUrl(String wechatEntrustUrl) {
        this.wechatEntrustUrl = wechatEntrustUrl;
    }
}
