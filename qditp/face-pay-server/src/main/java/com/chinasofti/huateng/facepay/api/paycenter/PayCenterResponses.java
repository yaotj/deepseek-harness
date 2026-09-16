package com.chinasofti.huateng.facepay.api.paycenter;

import com.alibaba.fastjson2.JSONObject;

/**
 * 回给支付中心的应答报文，形态 {@code {code, msg}}，逐字照搬旧 {@code PayCenterResult} +
 * {@code PayCenterErrorCodeEnum}。
 *
 * <p><b>应答语义决定对端是否重推</b>：只有 {@code code=0} 才表示我方已受理；返回失败码，
 * 支付中心会按其重试策略再推。因此<b>状态不明时 MUST 返回失败让对端重推</b>，
 * NEVER 为了「让日志干净」而回成功——那等于把一笔支付结果永久丢掉。</p>
 */
public final class PayCenterResponses {

    /** 受理成功。 */
    public static final String CODE_SUCCESS = "0";

    /** 失败/系统异常。 */
    public static final String CODE_FAIL = "-1";

    /** 订单不存在。 */
    public static final String CODE_ORDER_NOT_EXIST = "2001";

    private PayCenterResponses() {
    }

    /** 已受理，支付中心不再重推。 */
    public static JSONObject success() {
        return body(CODE_SUCCESS, "成功");
    }

    /** 未受理，支付中心按其策略重推。 */
    public static JSONObject fail() {
        return body(CODE_FAIL, "失败/系统异常");
    }

    /** 订单不存在。 */
    public static JSONObject orderNotExist() {
        return body(CODE_ORDER_NOT_EXIST, "订单不存在");
    }

    /** 自定义文案的失败应答。 */
    public static JSONObject fail(String msg) {
        return body(CODE_FAIL, msg);
    }

    private static JSONObject body(String code, String msg) {
        JSONObject json = new JSONObject();
        json.put("code", code);
        json.put("msg", msg);
        return json;
    }
}
