package com.chinasofti.huateng.facepay.channel.paycenter;

import java.util.LinkedHashMap;
import java.util.Map;

/** 测试用的 {@link PayCenterResult} 构造入口。 */
public final class PayCenterResults {

    private PayCenterResults() {
    }

    /** 对端答上来了。 */
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
