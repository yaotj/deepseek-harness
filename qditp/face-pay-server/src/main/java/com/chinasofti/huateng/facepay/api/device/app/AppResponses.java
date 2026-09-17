package com.chinasofti.huateng.facepay.api.device.app;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

/** APP 侧响应报文组装，逐字照搬旧 {@code AppOrderResult}。 */
public final class AppResponses {

    /** 成功。 */
    public static final String CODE_SUCCESS = "0000";

    /** 下单与请求支付信息的参数校验失败。 */
    public static final String CODE_INVALID_ORDER_PARAM = "8001";

    /** 查询类接口的参数校验失败。 */
    public static final String CODE_INVALID_PARAM = "8003";

    /** 支付中（{@code requestPayResult} 拿不到明确结果时）。 */
    public static final String CODE_PAYING = "8999";

    /** service 层通用失败。 */
    public static final String CODE_FAIL = "9999";

    private AppResponses() {
    }

    /** 只有 {@code retCode/retMsg}。 */
    public static JSONObject success() {
        return body(CODE_SUCCESS, "成功");
    }

    /** 下单成功：{@code retCode/retMsg + orderNo}。 */
    public static JSONObject orderNo(String orderNo) {
        JSONObject result = body(CODE_SUCCESS, "成功");
        result.put("orderNo", orderNo);
        return result;
    }

    /** IF8A-11 请求支付信息成功。 */
    public static JSONObject payInfo(String payChannelCode, String paymentInfo) {
        JSONObject result = body(CODE_SUCCESS, "成功");
        result.put("payChannelCode", payChannelCode);
        result.put("paymentInfo", paymentInfo);
        result.put("signType", "00");
        result.put("sign", "");
        return result;
    }

    /**
     * IF8A-18 支付结果查询成功。
     *
     * @param payResult 只有 {@code SUCCESS} / {@code FAIL} 两种取值（不是 FAILED）
     * @param payDate   支付时间，原样回吐、NEVER 把 null 兜成空串。
     */
    public static JSONObject payResult(String tradeNo, String payResult, Long payAmount, String payDate) {
        JSONObject result = body(CODE_SUCCESS, "成功");
        result.put("tradeNo", tradeNo);
        result.put("payResult", payResult);
        result.put("payAmount", payAmount == null ? null : String.valueOf(payAmount));
        result.put("payDate", payDate);
        return result;
    }

    /** 激活订单列表：{@code retCode/retMsg + orderList}。 */
    public static JSONObject orderList(JSONArray orderList) {
        JSONObject result = body(CODE_SUCCESS, "成功");
        result.put("orderList", orderList);
        return result;
    }

    /**
     * 退款受理 / 退款结果，两个接口同一形态。
     *
     * @param refundResult {@code PROCESSING} / {@code SUCCESS} / {@code FAIL}
     */
    public static JSONObject refund(String orderNo, String refundDate, Long refundAmount,
                                    String refundResult, String refundResultDesc, String notifyUrl) {
        JSONObject result = body(CODE_SUCCESS, "成功");
        result.put("orderNo", orderNo);
        result.put("refundType", "00");
        result.put("refundDate", refundDate);
        result.put("refundAmount", refundAmount == null ? null : String.valueOf(refundAmount));
        result.put("refundResult", refundResult);
        result.put("refundResultDesc", refundResultDesc);
        if (notifyUrl != null) {
            result.put("notifyUrl", notifyUrl);
        }
        return result;
    }

    /** 失败响应，带指定错误码。 */
    public static JSONObject fail(String retCode, String retMsg) {
        return body(retCode, retMsg);
    }

    /** service 层通用失败（{@code 9999}），旧 {@code failMessage}。 */
    public static JSONObject failMessage(String retMsg) {
        return body(CODE_FAIL, retMsg);
    }

    private static JSONObject body(String retCode, String retMsg) {
        JSONObject result = new JSONObject();
        result.put("retCode", retCode);
        result.put("retMsg", retMsg);
        return result;
    }
}
