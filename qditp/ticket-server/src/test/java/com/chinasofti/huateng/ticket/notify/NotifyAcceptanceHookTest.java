package com.chinasofti.huateng.ticket.notify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 两条外发链路**应答判定口径**的特征测试。
 *
 * <p>{@code notify} 包里只有一件事绝对不能被「顺手统一」：**受理判定**。
 * 支付宝行程推送只判 HTTP 2xx（对方没有业务 retCode 约定），行业数据推送 MUST 同时判 retCode
 * （2026-09-11 实测 HTTP 200 + {@code retCode=7004}）。本类把两侧逐格钉死 ——
 * 谁把其中一侧改成另一侧的口径，这里立刻变红。
 *
 * <p>为什么只测钩子、不测整条 HTTP 链路：okhttp 的收发要靠 mockwebserver 才能在单测里跑，
 * 那是一个**新的测试依赖**，而 {@code FormDataNotifyTemplate#post} 里剩下的是纯样板
 * （建 Request、读 body、try-with-resources、catch 记 ERROR），没有分支判断。
 * 真正会改错的那一步已经被隔离成 {@code isAccepted}，**NEVER 为了「覆盖率好看」把 mockwebserver
 * 引进来** —— 要引也得先确认它能测出比钩子更多的东西。
 */
class NotifyAcceptanceHookTest {

    /** 钩子是纯函数，不碰这三个依赖，因此可以全传 null。 */
    private final AlipayTripNotifier alipayTripNotifier = new AlipayTripNotifier(null, null, null);
    private final IndustryDataNotifier industryDataNotifier = new IndustryDataNotifier(null, null, null);

    @ParameterizedTest(name = "httpSuccessful={0}, body={1} -> {2}")
    @CsvSource(nullValues = "NULL", value = {
            // 只看 HTTP，body 一律不参与判定
            "true,  '',                          true",
            "true,  NULL,                        true",
            "true,  '{\"retCode\":\"7004\"}',    true",
            "true,  这不是JSON,                   true",
            "false, '{\"retCode\":\"0000\"}',    false",
            "false, NULL,                        false",
    })
    void 支付宝行程推送只判HTTP2xx(boolean httpSuccessful, String responseBody, boolean expected) {
        // 第三行是关键：即使对方回了一个像失败码的东西，本链路也不据此判失败 ——
        // 支付宝侧没有这个字段约定，凭空判它等于自造契约。
        assertEqualsAccepted(expected, alipayTripNotifier.isAccepted(httpSuccessful, responseBody));
    }

    @ParameterizedTest(name = "httpSuccessful={0}, body={1} -> {2}")
    @CsvSource(nullValues = "NULL", value = {
            // 唯一受理条件：2xx + retCode=0000
            "true,  '{\"retCode\":\"0000\"}',                          true",
            "true,  '{\"retCode\":\"0000\",\"retMsg\":\"成功\"}',        true",
            // HTTP 200 但业务失败 —— 2026-09-11 对无效卡号实测到的真实组合
            "true,  '{\"retCode\":\"7004\",\"retMsg\":\"处理过程出现错误!\"}', false",
            // 应答缺 retCode / 空体 / 不可解析为 JSON，一律算未受理
            "true,  '{\"retMsg\":\"成功\"}',                            false",
            "true,  '{}',                                              false",
            "true,  '',                                                false",
            "true,  NULL,                                              false",
            "true,  这不是JSON,                                         false",
            // HTTP 非 2xx 时即便体里是 0000 也算未受理
            "false, '{\"retCode\":\"0000\"}',                          false",
    })
    void 行业数据推送MUST同时判retCode(boolean httpSuccessful, String responseBody, boolean expected) {
        assertEqualsAccepted(expected, industryDataNotifier.isAccepted(httpSuccessful, responseBody));
    }

    /**
     * 两侧的**分歧点**单独钉一条：同一份「HTTP 200 + 7004」应答，两条链路结论必须相反。
     *
     * <p>这条用例存在的意义就是让「把两个 {@code isAccepted} 合并成一个」这件事无法悄悄发生。</p>
     */
    @Test
    void 同一份7004应答两条链路结论必须相反() {
        String body = "{\"retCode\":\"7004\",\"retMsg\":\"处理过程出现错误!\"}";
        assertTrue(alipayTripNotifier.isAccepted(true, body), "支付宝链路只判 2xx，MUST 判受理");
        assertFalse(industryDataNotifier.isAccepted(true, body), "行业数据链路 MUST 判未受理");
    }

    private void assertEqualsAccepted(boolean expected, boolean actual) {
        if (expected) {
            assertTrue(actual);
        } else {
            assertFalse(actual);
        }
    }
}
