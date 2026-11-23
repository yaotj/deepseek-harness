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

    /**
     * 支付中心明确回显渠道（用于验证「换渠道重下」时对返回渠道的校验）。
     *
     * <p>不像 {@link #answered} 那样固定回 {@code WECHAT}：那条路径下 {@code payChannelCode} 缺省，
     * 生产代码会退回本次请求渠道，测不出「支付中心仍挂旧渠道」。</p>
     */
    public static PayCenterResult answeredWithChannel(String code, String data, String payChannelCode) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (data != null) {
            body.put("data", data);
        }
        body.put("paymentVendor", payChannelCode);
        body.put("payChannelCode", payChannelCode);
        body.put("orderNo", "PC20260914001");
        return PayCenterResult.answered(code, "mock", body, "{\"code\":\"" + code + "\"}", 12L);
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
