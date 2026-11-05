package com.chinasofti.huateng.facepay.channel.paycenter;

import java.util.Collections;
import java.util.Map;

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

    public boolean isSuccessCode() {
        return "0".equals(code);
    }

    public PayCenterStatus status() {
        return PayCenterStatus.fromCode(string("status"));
    }

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
