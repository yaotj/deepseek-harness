package com.chinasofti.huateng.facepay.channel.paycenter;

import java.util.Collections;
import java.util.Map;

/**
 * 支付中心一次交互的结果，供上层原样落 {@code F2F_PAYMENT}（含耗时与原始报文）。
 *
 * <h2>三种结局必须区分开</h2>
 * <ul>
 *   <li><b>业务成功</b>：{@link #isSuccessCode()} 为真（{@code code=0}），此时再看
 *       {@link #status()} 决定 SUCCESS / FAILED / UNKNOWN。</li>
 *   <li><b>业务失败</b>：{@code code != 0}，对端明确拒绝，可判 FAILED。</li>
 *   <li><b>没答上来</b>：{@link #isTransportFailed()} 为真（超时、连不上、HTTP 非 2xx、
 *       响应体不是合法 JSON）。此时 <b>NEVER 判 FAILED</b>——钱可能已经扣了。MUST 落
 *       {@code PAY_STATUS='UNKNOWN'}，由查询接口或回调收口。</li>
 * </ul>
 *
 * <p>这是旧实现最大的坑：{@code PayCenterServiceImpl.callPayCenter} 异常后 {@code return null}，
 * 调用方一律按「支付中心返回结果为空」写 {@code status=FAILED}，把「不知道」当成了「没付成功」。</p>
 */
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
