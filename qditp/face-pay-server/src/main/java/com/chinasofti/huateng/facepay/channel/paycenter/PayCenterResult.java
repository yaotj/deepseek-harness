package com.chinasofti.huateng.facepay.channel.paycenter;

import java.util.Collections;
import java.util.Map;

/** 支付中心一次交互的结果，供上层原样落 {@code F2F_PAYMENT}（含耗时与原始报文）。 */
public final class PayCenterResult {

    private final String code;

    private final String msg;

    private final Map<String, Object> data;

    private final String rawResponse;

    private final long costMs;

    private final boolean transportFailed;

    private final String failureReason;

    private PayCenterResult(String code, String msg, Map<String, Object> data, String rawResponse,
                            long costMs, boolean transportFailed, String failureReason) {
        this.code = code;
        this.msg = msg;
        this.data = data == null ? Collections.emptyMap() : data;
        this.rawResponse = rawResponse;
        this.costMs = costMs;
        this.transportFailed = transportFailed;
        this.failureReason = failureReason;
    }

    static PayCenterResult answered(String code, String msg, Map<String, Object> data,
                                    String rawResponse, long costMs) {
        return new PayCenterResult(code, msg, data, rawResponse, costMs, false, null);
    }

    static PayCenterResult unknown(String failureReason, String rawResponse, long costMs) {
        return new PayCenterResult(null, null, null, rawResponse, costMs, true, failureReason);
    }

    /** 支付中心公共响应码 {@code 0} 才是受理成功，与旧 {@code PayCenterErrorCodeEnum.SUCCESS} 一致。 */
    public boolean isSuccessCode() {
        return "0".equals(code);
    }

    /** {@code data.status}；缺失或不认识返回 {@code null}，按 UNKNOWN 处理。 */
    public PayCenterStatus status() {
        return PayCenterStatus.fromCode(string("status"));
    }

    /** 取 {@code data} 里的字符串字段，如 {@code orderNo} / {@code channelOrderNo} / {@code paymentVendor} / {@code data}。 */
    public String string(String key) {
        Object value = data.get(key);
        return value == null ? null : String.valueOf(value);
    }

    public String getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public String getRawResponse() {
        return rawResponse;
    }

    public long getCostMs() {
        return costMs;
    }

    public boolean isTransportFailed() {
        return transportFailed;
    }

    public String getFailureReason() {
        return failureReason;
    }

    @Override
    public String toString() {
        return "PayCenterResult{code=" + code
                + ", msg=" + msg
                + ", status=" + string("status")
                + ", costMs=" + costMs
                + ", transportFailed=" + transportFailed
                + ", failureReason=" + failureReason
                + '}';
    }
}
