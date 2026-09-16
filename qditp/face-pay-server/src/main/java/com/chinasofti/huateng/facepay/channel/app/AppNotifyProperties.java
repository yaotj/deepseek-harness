package com.chinasofti.huateng.facepay.channel.app;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * APP 网关通知地址（IF8B-04/05/06/07 出向）。
 *
 * <h2>两条硬性约定</h2>
 * <ol>
 *   <li><b>只配完整 URL，NEVER 用 base + path 拼接。</b>2026-08-26 事故：`pay-sign-server`
 *       5 条路径漏了 `/v1`，网关返回 `code=600 操作失败`（不是 404），免密扣款自上线起
 *       零条成功却没人发现。拼接一旦漏段极难定位，见 AGENTS.md §8。</li>
 *   <li><b>默认值一律为空</b>（{@code ${ENV:}} 形态由 K8s 注入）。旧
 *       `collect-pay-server/application.yml:36-39` 三条通知地址硬编码了
 *       `dtcustomer.bestonepay.com/testngbackV2/...`——<b>`testngbackV2` 是测试环境残留</b>，
 *       属 AGENTS.md §8 记录的未修复 P0。本模块 NEVER 继承这些值。</li>
 * </ol>
 *
 * <p>URL 未配置时 {@code F2fNotifyJob} 把该任务记为失败并退避重试，
 * <b>不会静默丢弃</b>——通知任务已落库，配好地址后自动补发。</p>
 */
@ConfigurationProperties(prefix = "f2f.notify.app")
public class AppNotifyProperties {

    /** 出票成功通知，对应旧 {@code pay.center.notice-app-taketicketresult-url}。 */
    private String takeTicketOkUrl;

    /** 出票失败通知，对应旧 {@code pay.center.notice-app-taketicketfailureresult-url}。 */
    private String takeTicketFailUrl;

    /** 退款结果通知，对应旧 {@code pay.center.notice-app-refundresult-url}。 */
    private String refundResultUrl;

    /**
     * APP 退款受理响应里回吐给 APP 的 {@code notifyUrl}，对应旧
     * {@code pay.center.app-refund-notice-url}（集群 env 实测指向 fep-app 的
     * {@code /ci/app/receiveRefundResult}，仓库 yml 里那条 payNotice 是过期值）。
     *
     * <p><b>与 {@link #refundResultUrl} 是两个不同的值，NEVER 合并</b>：这条只出现在
     * 响应体里、我方从不请求它；那条是 {@code F2fNotifyJob} 真正 POST 的出向地址。
     * 旧实现两个键各配各的，合并会改变响应内容。</p>
     */
    private String refundNoticeUrl;

    /**
     * 支付结果通知。旧实现无此出向通知，为 IF8B 预留。
     *
     * <p><b>看着像死配置，其实不是：NEVER 删。</b>{@code F2fNotifyJob.urlOf()} 的
     * {@code case TYPE_PAY_RESULT} 在读它（`F2fNotifyJob.java:107`），删字段直接编译失败。
     * 2026-09-15 曾据一次<b>大小写敏感</b>的 grep（只搜 {@code payResultUrl}、漏掉
     * {@code getPayResultUrl}）误判成零读取方而删除，构建即报「找不到符号」。
     * 判断某属性有无读取方 MUST 用不区分大小写的 grep 或直接搜 {@code get<Name>}。</p>
     *
     * <p><b>生产者已于 2026-09-15（1.0.36 / ADR-D89）补齐</b>：
     * {@code F2fPayCenterFlow.enqueuePayResultNotify}，覆盖三个支付成功收口点；
     * 集群 env {@code NOTIFY_APP_PAY_RESULT_URL} 也已配上真实地址。
     * <b>本段此前写的「没有任何生产者、env 已删除、分支走不到」已作废，NEVER 回退。</b></p>
     */
    private String payResultUrl;

    /**
     * ITP 报文信封的 {@code providerId}，默认 {@code 06}。
     *
     * <p><b>下面这六个信封字段 NEVER 删</b>：2026-09-15 实测（ADR-D89）该 APP 网关
     * <b>只认 ITP 信封</b> —— 同一份业务 JSON，裸 body 发过去返
     * {@code retCode=7004 处理过程出现错误!}，包成信封（{@code bizData} 装业务 JSON）
     * 立刻变成 {@code 7001 找不到对应的数据}（= 已解析出订单号、只是它库里没这单）。
     * 取值逐字对齐旧 {@code collect-pay-server/application.yml:40~44} 的 {@code app.*}
     * （`providerId=06` / `charset=UTF-8` / `format=json` / `signType=00`），
     * {@code sign} 与 {@code deviceId} 旧实现都是空串。</p>
     *
     * <p><b>但「信封就能被受理」只对出票 / 退款那几条成立</b>：2026-09-16 实测（ADR-D102）
     * {@code receivePaymentResult} 无论信封还是裸 JSON、{@code deviceId}/{@code sign} 空与非空、
     * {@code bizData} 3 键 / 7 键 / 只带 {@code orderNo}，<b>七组全返 {@code 7004}</b>，
     * 而同批打 {@code receiveTakeTicketResult} 返 {@code 7001}。
     * <b>NEVER 为了那条通知去动这六个字段的取值</b>，成因在对端。</p>
     */
    private String providerId = "06";

    /** 信封 {@code charset}。 */
    private String charset = "UTF-8";

    /** 信封 {@code format}。 */
    private String format = "json";

    /**
     * 信封 {@code signType}，默认 {@code 00}（免签）。
     *
     * <p>出向通知当前<b>不加签</b>，与 pay-sign / ticket / collect-pay 三条链路的现状一致
     * （那三处也都是 {@code signType=00} + 空 {@code sign}）。与 AGENTS.md §5.2
     * 的鉴权要求冲突、属测试期敞口，上线前 MUST 补；<b>NEVER 在本模块自造签名逻辑</b>，
     * 要加 MUST 对齐现有验签实现。</p>
     */
    private String signType = "00";

    /** 信封 {@code sign}，免签时为空串。 */
    private String sign = "";

    /** 信封 {@code deviceId}，出向通知无设备概念，旧实现固定空串。 */
    private String deviceId = "";

    /** 连接超时，毫秒。 */
    private int connectTimeoutMs = 3000;

    /** 读超时，毫秒。通知是异步任务，可以比设备链路给得宽松些。 */
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
