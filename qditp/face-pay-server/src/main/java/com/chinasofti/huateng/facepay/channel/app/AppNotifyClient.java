package com.chinasofti.huateng.facepay.channel.app;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * APP 网关通知客户端（出向）。
 *
 * <h2>与 {@code PayCenterClient} 一致的两条约定</h2>
 * <ol>
 *   <li><b>NEVER 抛异常、NEVER 返回 null。</b>所有失败都收敛成
 *       {@link AppNotifyResult#failed}，由调用方决定重试还是放弃。
 *       通知链路上一个未捕获异常会让整批扫表任务停摆。</li>
 *   <li>用 JDK {@code HttpClient} 而不是 {@code rpc} 模块的 {@code ProxyWebClient}：
 *       APP 网关是<b>外部系统</b>，不走 `service.*.url` 那套内部服务发现。</li>
 * </ol>
 *
 * <h2>报文形态：ITP 信封 + {@code bizData}，NEVER 退回裸 JSON</h2>
 * <p>2026-09-15（ADR-D89）用同一份业务字段对同一个 URL 做了 A/B 实测，结论是
 * <b>该网关只认信封</b>：</p>
 * <ul>
 *   <li>裸 JSON body（{@code Content-Type: application/json}）→ {@code retCode=7004 处理过程出现错误!}</li>
 *   <li>{@code x-www-form-urlencoded} 信封 + {@code bizData} 装同一份 JSON →
 *       {@code retCode=7001 找不到对应的数据}（= 已解析出订单号，只是对端库里没这单）</li>
 * </ul>
 * <p>7001 与 7004 的差别就是「解析到了」与「没解析到」。改本类的报文形态前 MUST 重跑这个 A/B，
 * <b>NEVER 只凭「HTTP 200 且返了 retCode」就认为形态没问题</b> —— 两种形态都是 200。
 * pay-sign / ticket / collect-pay 三条同族链路一直发的都是信封（前三者用 okhttp
 * {@code MultipartBody.FORM}，实测 {@code multipart} 与 {@code urlencoded} 对端都能解），
 * <b>face-pay 此前是四条里唯一发裸 JSON 的，因此 IF8B-04/05/06/07 全都从未被对端受理过</b>。</p>
 *
 * <h2>⚠️ 「7004 = 我方形态不对」这条判断只对出票 / 退款那几条成立，
 * 对 IF8B-05 {@code receivePaymentResult} 已被实测推翻（2026-09-16，ADR-D102）</h2>
 * <p>该端点上跑过 7 组探针（信封 / 裸 JSON、{@code deviceId} 与 {@code sign} 空与非空的四种组合、
 * 3 键 / 7 键 / 只带 {@code orderNo} 三种 {@code bizData}），<b>全部返 {@code 7004}</b>；
 * 而同一域名、同一批次、同一信封打 {@code receiveTakeTicketResult} 返的是
 * {@code 7001 找不到对应的数据}（正常的「解析到了但库里没这单」）。更关键的一条：
 * <b>拿同一个 {@code orderNo} 重发时它返 {@code 7005 当前数据已经在处理中}</b> ——
 * 说明它<b>解析到了订单号并落了记录</b>，随后处理必然出错。
 * 因此 IF8B-05 的 7004 是<b>对端该端点自身的处理异常</b>，不是我方报文形态或字段缺失。</p>
 * <p><b>NEVER 再为这条通知改报文形态或补字段</b>：F2F_NOTIFY_TASK 里 12 条 {@code PAY_RESULT}
 * 全部 5 次重试耗尽转 {@code GIVEUP}，改形态一次也不会变。MUST 找 APP 侧核对该端点实现，
 * 或先确认它是否已具备对接条件；同样 <b>NEVER 把 7004 加进成功码把它藏起来</b>（见下一段）。</p>
 *
 * <h2>⚠️ 上一节那条 NEVER 只覆盖 IF8B-05，<b>NEVER 外推到同族其它通知</b>（2026-09-16，ADR-D112）</h2>
 * <p>同日又拿一条已 {@code GIVEUP} 的真实订单在**同一个域名**上做了 A/B，结论与 IF8B-05 相反：</p>
 * <ul>
 *   <li>{@code receiveTakeTicketFaultResult}（IF8B-07）原 5 键 → {@code 7004}；
 *       {@code bizData} 首位补 {@code userId} → {@code 7005 当前数据已经在处理中}（已受理）</li>
 *   <li>{@code receiveTakeTicketResult}（IF8B-06）原 4 键、<b>不带</b> {@code userId} → {@code 0000 成功}</li>
 *   <li>{@code receivePaymentResult}（IF8B-05）带真实 {@code userId} 的 APP 单 → 2026-09-16 14:06:25
 *       实测 <b>SUCCESS</b>；同批不带 {@code userId} 的设备单仍全 {@code 7004}</li>
 * </ul>
 * <p>因此该网关的 {@code 7004} <b>至少有两种成因</b>：①我方 {@code bizData} 缺该端点必需的键
 * （IF8B-07 的 {@code userId}）；②对端该端点自身处理异常（IF8B-05 的 7 组探针）。
 * <b>判据只能靠逐端点 A/B 实测，NEVER 拿某一个端点的结论套到别的端点上</b>；
 * 上一节那段 NEVER 的适用范围**仅限 IF8B-05**，且它现在也只对「设备单」成立
 * —— 带真实 {@code userId} 的 APP 单已经能通。</p>
 *
 * <h2>「投递成功」的判定口径</h2>
 * <p>HTTP 非 2xx 一律判失败。2xx 时再看应答体：</p>
 * <ul>
 *   <li>能解析出 {@code retCode} 或 {@code code} → <b>按它判</b>，只有明确成功码才算投递成功；</li>
 *   <li>解析不出来（空体、非 JSON、没有这两个键）→ <b>按 2xx 算投递成功但打 WARN</b>。</li>
 * </ul>
 * <p>后一条是有意的折中：若改成「解析不出就判失败」，一个只回 {@code OK} 纯文本的网关会让
 * 每条通知都重试到 {@code GIVEUP}；若改成「2xx 就无条件成功」，网关回
 * {@code retCode=9999} 也会被当成投递完成、通知永久丢失。</p>
 * <p><b>成功码只认 {@code 0000} / {@code code=0}，NEVER 顺手把 {@code 7004} 加进来</b>：
 * `pay-sign-server` 的 `app.notify.success-ret-codes=0000,7004` 是**那条链路**按「7004 = 已处理/重复通知」
 * 拿到确认后配的（`docs/business/pay-sign.md:234`），而 ticket-server 侧同一个码实测是
 * 「对无效卡号的处理失败」、被判成失败（ADR-D61 明确要求两条链路口径 NEVER 统一）。
 * 本链路的 7004 是**对端处理阶段出错**（IF8B-05 那条见上一节的实测矩阵），
 * 加成功码等于把「对端根本没受理」永久记成投递完成。</p>
 */
@Component
public class AppNotifyClient {

    /** APP 侧成功码，与设备侧 {@code retCode} 同族。 */
    private static final String RET_CODE_SUCCESS = "0000";

    /** 部分网关用 {@code code=0} 表示成功（支付中心那套口径）。 */
    private static final String CODE_SUCCESS = "0";

    /** 信封 {@code timestamp} 格式，与设备链路一致。 */
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final Logger log = LoggerFactory.getLogger(AppNotifyClient.class);

    private final AppNotifyProperties properties;

    private final HttpClient httpClient;

    public AppNotifyClient(AppNotifyProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .build();
    }

    /**
     * 投递一条通知。
     *
     * @param url         完整 URL；为空时直接返回失败（配置缺失也要留痕，NEVER 静默跳过）
     * @param payloadJson 业务报文 JSON，作为信封的 {@code bizData} 字段值发送
     */
    public AppNotifyResult post(String url, String payloadJson) {
        if (url == null || url.isBlank()) {
            return AppNotifyResult.failed("通知地址未配置，检查 f2f.notify.app.*-url");
        }
        String bizData = payloadJson == null ? "{}" : payloadJson;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                    .header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody(bizData), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return evaluate(url, response.statusCode(), response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("通知投递被中断, url={}", url, e);
            return AppNotifyResult.failed("投递被中断");
        } catch (Exception e) {
            log.error("通知投递异常, url={}", url, e);
            return AppNotifyResult.failed(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * 拼 ITP 信封表单体，{@code bizData} 放业务 JSON。
     *
     * <p>{@code timestamp} 每次投递按当前时刻重取（重试时也会换新值），格式与设备链路一致
     * {@code yyyyMMddHHmmss}。<b>八个字段一个都不能少</b>——对端缺字段时返的还是那个笼统的
     * {@code 7004}，看不出少了哪个。</p>
     */
    private String formBody(String bizData) {
        StringBuilder body = new StringBuilder();
        append(body, "providerId", properties.getProviderId());
        append(body, "charset", properties.getCharset());
        append(body, "format", properties.getFormat());
        append(body, "timestamp", LocalDateTime.now().format(TIMESTAMP_FORMATTER));
        append(body, "deviceId", properties.getDeviceId());
        append(body, "signType", properties.getSignType());
        append(body, "sign", properties.getSign());
        append(body, "bizData", bizData);
        return body.toString();
    }

    private static void append(StringBuilder body, String name, String value) {
        if (!body.isEmpty()) {
            body.append('&');
        }
        body.append(name).append('=')
                .append(URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8));
    }

    /** 判定应答，口径见类注释。 */
    private AppNotifyResult evaluate(String url, int statusCode, String body) {
        if (statusCode < 200 || statusCode >= 300) {
            return AppNotifyResult.failed("HTTP " + statusCode + ", body=" + abbreviate(body));
        }
        JSONObject json = parse(body);
        String retCode = json == null ? null : json.getString("retCode");
        String code = json == null ? null : json.getString("code");
        if (retCode == null && code == null) {
            log.warn("APP 通知应答无法判定成功码，按 HTTP {} 记为已投递（联调时 MUST 核对应答体）,"
                    + " url={}, body={}", statusCode, url, abbreviate(body));
            return AppNotifyResult.ok();
        }
        if (RET_CODE_SUCCESS.equals(retCode) || CODE_SUCCESS.equals(code)) {
            return AppNotifyResult.ok();
        }
        return AppNotifyResult.failed("对端未受理, retCode=" + retCode + ", code=" + code);
    }

    private static JSONObject parse(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return JSON.parseObject(body);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String abbreviate(String body) {
        if (body == null) {
            return null;
        }
        return body.length() <= 200 ? body : body.substring(0, 200) + "...";
    }

    public AppNotifyProperties properties() {
        return properties;
    }
}
