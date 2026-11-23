package com.chinasofti.huateng.fep.app.controller;

import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.rpc.facepay.FacePayAppClient;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * APP 域网关的下游路径映射锁定测试。
 *
 * <p>这 8 条路径是对外契约（APP → fep-app → face-pay）的最后一跳，写错的症状是 404/403 而不是编译错误，
 * 因此 MUST 逐条断言。<b>两条前缀不同</b>：7 条在 {@code /ci/app}，{@code requestActiveTicket} 在
 * {@code /itptvm/ci/tvm} —— 曾被误以为"face-pay 没承接"，实为路径前缀不同。</p>
 *
 * <p>用匿名子类拦下 {@code postFormAndGetResponse} 而不 mock：{@code ProxyWebClient} 子类在 JDK 25 上
 * Mockito 无法 mock（{@code MockitoException: cannot mock this class}）。</p>
 */
class FacePayAppClientPathTest {

    private final List<String> calledUrls = new ArrayList<>();

    private final FacePayAppClient client =
            new FacePayAppClient("http://face-pay.test:8080", false, WebClient.builder()) {
                @Override
                public String postFormAndGetResponse(String url, Map<String, String> formData) {
                    calledUrls.add(url);
                    return "{\"retCode\":\"0000\"}";
                }
            };

    private final Map<String, String> form = new LinkedHashMap<>(Map.of("bizData", "{}"));

    @Test
    void mapsEveryAppEndpointToFacePayPath() {
        assertPath("/ci/app/requestOrder", client.requestOrder(form));
        assertPath("/ci/app/requestPaymentInfo", client.requestPaymentInfo(form));
        assertPath("/ci/app/requestPayResult", client.requestPayResult(form));
        assertPath("/ci/app/requestRefundTicket", client.requestRefundTicket(form));
        assertPath("/ci/app/requestRefundTicketResult", client.requestRefundTicketResult(form));
        assertPath("/ci/app/requestPreActiveOrderList", client.requestPreActiveOrderList(form));
        assertPath("/itptvm/ci/tvm/requestActiveTicket", client.requestActiveTicket(form));
        assertPath("/ci/app/receiveRefundResult", client.receiveRefundResult(form));
        assertEquals(8, calledUrls.size());
    }

    @Test
    void neverTargetsDeprecatedCollectPay() {
        client.requestPaymentInfo(form);
        client.requestActiveTicket(form);
        assertTrue(calledUrls.stream().noneMatch(url -> url.contains("collect")),
                "下游 MUST 只有 face-pay，collect-pay 已于 2026-10-13 弃用");
    }

    private void assertPath(String expected, JSONObject response) {
        assertEquals(expected, calledUrls.get(calledUrls.size() - 1));
        assertEquals("0000", response.getString("retCode"));
    }
}
