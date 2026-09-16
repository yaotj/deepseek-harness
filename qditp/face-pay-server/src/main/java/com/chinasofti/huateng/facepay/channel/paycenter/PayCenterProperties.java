package com.chinasofti.huateng.facepay.channel.paycenter;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 支付中心对接配置，前缀 {@code pay.center}。
 *
 * <p>四条业务 URL 都是<b>完整地址</b>，NEVER 退回「base + path 拼接」——支付中心路径带
 * {@code /v1} 版本段，拆两半后漏 {@code /v1} 时对端返回 {@code code=600 操作失败}（不是 404），
 * 极难定位（见 AGENTS.md §8）。</p>
 *
 * <p>{@code privateKey} / {@code jhmKey} 是密钥，只由 K8s Secret 注入，NEVER 打日志。</p>
 */
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

    /**
     * 补款单（IF8A-26，{@code SP} 前缀）专用的支付结果回调地址。
     *
     * <p><b>为什么不能共用 {@link #payNoticeUrl}</b>：那个键的实际值是 TVM 端点
     * （集群 env {@code PAY_CENTER_PAY_NOTICE_URL} 指向 {@code /itptvm/ci/tvm/payNotice}），
     * 而该端点只查 {@code F2F_ORDER}，补款单落在 {@code SUPPLEMENT_ORDER} ⇒ 回调恒返
     * {@code 2001 订单不存在}、对端反复重推，支付成功只能靠 5 分钟一轮的收敛任务兜住
     * （2026-09-16 实测延迟约 3 分钟，见 ADR-D103）。</p>
     *
     * <p><b>留空即回落到 {@link #payNoticeUrl}</b>：env 没注入时行为与改动前完全一致，
     * NEVER 让它送出空串 —— 网关文档该字段是「否 / 不传取默认」，送空串的行为未实测。</p>
     */
    private String supplementNoticeUrl;

    /** 订单超时秒数，旧实现固定 180。 */
    private long orderTimeOutSeconds = 180L;

    /** 行业类型，旧实现固定 1（地铁）。 */
    private String industryType = "1";

    /** 退款原因，旧实现取自 pay.center.refundReason。 */
    private String refundReason;

    /** HTTP 连接超时毫秒。 */
    private int connectTimeoutMs = 10000;

    /** HTTP 读超时毫秒。旧实现是 60 秒，收窄到 30 秒以缩短虚拟线程阻塞窗口。 */
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

    /**
     * 补款单实际使用的回调地址：配了就用补款专用的，没配回落到通用的。
     *
     * <p>回落分支存在的意义是「env 未注入时行为与改动前完全一致」，
     * <b>NEVER 改成直接返回 {@link #supplementNoticeUrl}</b>。</p>
     */
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
