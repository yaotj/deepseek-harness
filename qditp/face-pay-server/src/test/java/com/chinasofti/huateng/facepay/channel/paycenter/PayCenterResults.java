package com.chinasofti.huateng.facepay.channel.paycenter;

import java.util.LinkedHashMap;
import java.util.Map;

public final class PayCenterResults {

    private PayCenterResults() {
    }

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

    public static PayCenterResult transportFailed(String failureReason) {
        return PayCenterResult.unknown(failureReason, null, 5000L);
    }

    public static PayCenterResult answeredRaw(String code, String rawStatus, String data) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (rawStatus != null) {
            body.put("status", rawStatus);
        }
        if (data != null) {
            body.put("data", data);
        }
        return PayCenterResult.answered(code, "mock", body, "{\"code\":\"" + code + "\"}", 12L);
    }
}
