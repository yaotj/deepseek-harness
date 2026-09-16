package com.chinasofti.huateng.paysign.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pay.sign")
public class PaySignProperties {
    private String apiVersion = "1.0";
    private String signType = "RSA2";
    private String charset = "UTF-8";
    private String merchantNo = "M100001";
    private String merchantPrivateKey;
    /**
     * 支付中心各接口的完整 URL，与 service.*.url、app.notify.*-url 保持一致的配置形态。
     *
     * <p>NEVER 退回「基础地址 + 相对路径拼接」：路径本身带版本号（`/api/v1/...`），
     * 拆成两半后漏 `/v1` 不会报 404、而是返回 `code=600 操作失败`，极难定位。
     * 2026-08-26 已因此导致免密扣款长期零成功。改动前 MUST 用只读接口
     * `/api/v1/contract/queryResult` 实测。</p>
     *
     * <p>不设默认值：地址随环境变化，缺配置时由 PayGatewayClient 直接抛异常暴露，
     * 比静默打到写死的默认域名安全。</p>
     */
    private String contractConfigUrl;
    private String contractUrl;
    private String contractAdvisoryUrl;
    private String contractResultUrl;
    private String terminationUrl;
    private String requestPayUrl;
    /** 支付查询（网关文档 §1.2 payQuery），只读接口，用于在拉黑前二次确认支付中心侧的真实状态。 */
    private String payQueryUrl;
    private String requestRefundUrl;
    /**
     * 退款查询（网关文档 §3.2 refundQuery，`docs/external/支付中心网关接口文档.md:342~368`），只读接口。
     *
     * <p>用于「退款请求已出网、本地停在 PROCESSING」时回查支付中心侧的真实退款状态
     * （{@code PaymentDomainServiceImpl.compensateRefundQuery}）。bizData 按原文
     * 「refundOrderNo 和 merchantRefundNo 至少填一个」，我方填 {@code refundOrderNo}
     * （§3.1 请求退款时上送的就是这个键，值即 {@code PAY_REFUND_DETAIL.REFUND_ORDER_NO}）。</p>
     *
     * <p><b>该地址尚未对真实网关实测</b>：路径取自规格原文、域名与 `/ngpayment-gateway/api/v1` 前缀
     * 与其余 8 条同源。按 AGENTS.md §8「外部网关地址 MUST 实测」，上线前 MUST 用一笔真实退款单
     * 打一次确认，**NEVER 因为「和别的 URL 长得一样」就当已验证** —— 2026-08-26 那次漏 `/v1`
     * 返的是 `code=600 操作失败`、不是 404，光看应答分不出是配错还是业务拒绝。</p>
     *
     * <p>不设默认值：缺配置时由 compensateRefundQuery 打 ERROR 并跳过本轮，
     * 比静默打到一个拼错的地址安全。</p>
     */
    private String refundQueryUrl;
    private String defaultNotifyUrl;
    private String requestPayNotifyUrl;
    private String alipayAppId = "60000157";
    private String alipayMerchantAppId = "2015101000413186";
    private String wechatAppId = "wx426a3015555a46be";
    private String wechatEntrustUrl = "https://api.mch.weixin.qq.com/papay/entrustweb";
    /** 测试阶段强制覆盖支付金额（分），0 表示不覆盖，使用调用方传入的实际金额。 */
    private int testForceAmount = 0;

    public int getTestForceAmount() { return testForceAmount; }
    public void setTestForceAmount(int testForceAmount) { this.testForceAmount = testForceAmount; }

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
