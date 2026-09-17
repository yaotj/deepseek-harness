package com.chinasofti.huateng.ticket.notify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 两条外发链路应答判定口径的特征测试。 */
class NotifyAcceptanceHookTest {

    /** 钩子是纯函数，不碰这三个依赖，因此可以全传 null。 */
    private final AlipayTripNotifier alipayTripNotifier = new AlipayTripNotifier(null, null, null);
    private final IndustryDataNotifier industryDataNotifier = new IndustryDataNotifier(null, null, null);

    @ParameterizedTest(name = "httpSuccessful={0}, body={1} -> {2}")
    @CsvSource(nullValues = "NULL", value = {
            "true,  '',                          true",
            "true,  NULL,                        true",
            "true,  '{\"retCode\":\"7004\"}',    true",
            "true,  这不是JSON,                   true",
            "false, '{\"retCode\":\"0000\"}',    false",
            "false, NULL,                        false",
    })
    void 支付宝行程推送只判HTTP2xx(boolean httpSuccessful, String responseBody, boolean expected) {
        assertEqualsAccepted(expected, alipayTripNotifier.isAccepted(httpSuccessful, responseBody));
    }

    @ParameterizedTest(name = "httpSuccessful={0}, body={1} -> {2}")
    @CsvSource(nullValues = "NULL", value = {
            "true,  '{\"retCode\":\"0000\"}',                          true",
            "true,  '{\"retCode\":\"0000\",\"retMsg\":\"成功\"}',        true",
            "true,  '{\"retCode\":\"7004\",\"retMsg\":\"处理过程出现错误!\"}', false",
            "true,  '{\"retMsg\":\"成功\"}',                            false",
            "true,  '{}',                                              false",
            "true,  '',                                                false",
            "true,  NULL,                                              false",
            "true,  这不是JSON,                                         false",
            "false, '{\"retCode\":\"0000\"}',                          false",
    })
    void 行业数据推送MUST同时判retCode(boolean httpSuccessful, String responseBody, boolean expected) {
        assertEqualsAccepted(expected, industryDataNotifier.isAccepted(httpSuccessful, responseBody));
    }

    /** 两侧的分歧点单独钉一条：同一份「HTTP 200 + 7004」应答，两条链路结论必须相反。 */
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
