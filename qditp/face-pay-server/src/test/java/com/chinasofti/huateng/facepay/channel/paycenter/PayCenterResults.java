package com.chinasofti.huateng.facepay.channel.paycenter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 测试用的 {@link PayCenterResult} 构造入口。
 *
 * <p><b>为什么放在这个包里</b>：{@code PayCenterResult} 的两个工厂
 * （{@code answered} / {@code unknown}）是包级私有的 —— 那是有意的，
 * 生产代码里只允许 {@link PayCenterClient} 造它。测试要造真实实例，
 * 只能同包放一个入口，<b>NEVER 为了测试把生产工厂改成 public</b>。</p>
 *
 * <p><b>也 NEVER 改成 mock</b>：{@code PayCenterResult} 是 {@code final} 类，
 * 本仓库当前的 Mockito mock maker 造不出它的替身 —— {@code when(result.isX())}
 * 会**直接调到真方法**，报 `UnfinishedStubbingException`（2026-09-14 实测）。
 * 何况它只是个值对象，造真的比 mock 更准。</p>
 */
public final class PayCenterResults {

    private PayCenterResults() {
    }

    /** 对端答上来了。{@code code="0"} 即受理成功；{@code status} 落在 {@code data.status}。 */
    public static PayCenterResult answered(String code, PayCenterStatus status, String data) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (status != null) {
            body.put("status", status.name());
        }
        if (data != null) {
            body.put("data", data);
        }
        body.put("paymentVendor", "WECHAT");
        body.put("channelOrderNo", "CH20260914001");
        body.put("orderNo", "PC20260914001");
        return PayCenterResult.answered(code, "mock", body, "{\"code\":\"" + code + "\"}", 12L);
    }

    /** 对端没答上来（超时 / 连不上 / 响应体不是合法 JSON）。 */
    public static PayCenterResult transportFailed(String failureReason) {
        return PayCenterResult.unknown(failureReason, null, 5000L);
    }
}
