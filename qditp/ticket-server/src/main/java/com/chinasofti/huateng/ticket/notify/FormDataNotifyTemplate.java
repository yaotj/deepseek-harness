package com.chinasofti.huateng.ticket.notify;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * form-data 外发推送骨架（Template Method）—— {@code notify} 包内两条外发链路共用的
 * 「组 form-data → POST → 读应答 → 判受理 → 统一日志与兜底」流程。
 *
 * <p>2026-09-14 从 {@code AlipayTripNotifier} 与 {@code IndustryDataNotifier} 上提（#19）。
 * 抽出来的理由**不是省那二十行 okhttp 样板**，而是把两条链路唯一真正不同的一步 ——
 * <b>应答判定</b> —— 变成一个 {@code abstract} 方法：
 * <ul>
 *   <li>支付宝行程推送：对方没有业务 retCode 约定，<b>只判 HTTP 2xx</b>；</li>
 *   <li>行业数据推送：HTTP 200 + {@code retCode=7004} 也是失败，<b>MUST 显式判 retCode</b>
 *       （2026-09-11 对无效卡号实测到该组合）。</li>
 * </ul>
 * 两者混在一个类、或共用一个「默认判定」时，很容易被「顺手统一」成其中一侧 —— 那会造成
 * 行业数据侧把 7004 当成功（运维完全看不见），或支付宝侧凭空多出一个对方不返的字段判定。
 * 现在**子类不实现 {@link #isAccepted} 就编译不过**，口径分歧由类型系统兜着。
 *
 * <p><b>本类不承载报文语义</b>：URL、bizData、deviceId 全部由子类传入，form-data 的 8 个公共字段
 * 仍归 {@link NotifyFormRequestFactory}。两条链路的报文都已联调通过，
 * <b>NEVER 在本类里加字段、改字段名或补签名</b>。
 *
 * <p><b>异常一律吞掉只记 ERROR</b>：两条链路都在调用方的异步任务里跑，抛出去没人接
 * （AGENTS.md §5.2 那条 `AFTER_COMMIT` 里抛异常的教训同理）。失败即丢是**当前的既有行为**，
 * 补偿（落库 + 扫表重试）要挂就挂在调用方对返回值的处置上，<b>NEVER 在本类里加重试</b> ——
 * 这两条链路都没有幂等键，盲目重试等于让对方收到重复行程。
 */
abstract class FormDataNotifyTemplate {

    private static final Logger log = LoggerFactory.getLogger(FormDataNotifyTemplate.class);

    private final OkHttpClient httpClient;
    private final NotifyFormRequestFactory formRequestFactory;

    protected FormDataNotifyTemplate(OkHttpClient httpClient, NotifyFormRequestFactory formRequestFactory) {
        this.httpClient = httpClient;
        this.formRequestFactory = formRequestFactory;
    }

    /**
     * 模板方法：组 form-data → POST → 交 {@link #isAccepted} 判定 → 统一日志。
     *
     * <p><b>{@code final}，NEVER 让子类覆写</b>：子类要改的只有判定口径，改流程说明抽象没选对。</p>
     *
     * @param targetUrl 目标地址，由子类从各自的配置键取
     * @param bizData   业务参数 JSON 串，原样进 form-data 的 {@code bizData}
     * @param deviceId  设备号，可为 null（支付宝行程链路恒传空串）
     * @param linkName  链路名，只用于日志，便于按链路过滤
     * @return true 仅当 {@link #isAccepted} 判定受理；HTTP 层异常一律 false
     */
    protected final boolean post(String targetUrl, String bizData, String deviceId, String linkName) {
        RequestBody requestBody = formRequestFactory.buildFormDataRequestBody(bizData, deviceId);
        Request httpRequest = new Request.Builder()
                .url(targetUrl)
                .post(requestBody)
                .build();

        log.info("调用{}, url={}, bizData={}", linkName, targetUrl, bizData);
        try (Response response = httpClient.newCall(httpRequest).execute()) {
            String responseBody = response.body() == null ? null : response.body().string();
            boolean accepted = isAccepted(response.isSuccessful(), responseBody);
            if (accepted) {
                log.info("调用{}成功, httpCode={}, response={}", linkName, response.code(), responseBody);
            } else {
                log.warn("调用{}未受理, httpCode={}, url={}, response={}",
                        linkName, response.code(), targetUrl, responseBody);
            }
            return accepted;
        } catch (Exception e) {
            log.error("调用{}异常, url={}, bizData={}", linkName, targetUrl, bizData, e);
            return false;
        }
    }

    /**
     * 应答判定钩子 —— <b>两条链路口径不同，本类刻意不给默认实现</b>。
     *
     * <p>加默认实现就等于给「顺手统一」开了口子：谁忘了覆写都会静默沿用另一条链路的口径。</p>
     *
     * @param httpSuccessful okhttp 的 {@code Response#isSuccessful}，即 HTTP 2xx
     * @param responseBody   应答体，可能为 null（对方无 body）或非 JSON
     *                       （行业数据那条实测 {@code Content-Type} 是 text/plain 但体是 JSON）
     */
    protected abstract boolean isAccepted(boolean httpSuccessful, String responseBody);
}
