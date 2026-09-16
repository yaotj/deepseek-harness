package com.chinasofti.huateng.facepay.api.device.app;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

/**
 * APP 侧响应报文组装，逐字照搬旧 {@code AppOrderResult}。
 *
 * <p><b>这是第四套错误码族</b>：成功 {@code 0000}，失败按端点分别用
 * {@code 8001}（下单/请求支付信息的参数校验）、{@code 8003}（查询类的参数校验）、
 * {@code 8999}（支付中）、{@code 9999}（service 层通用失败）。
 * 旧实现在同一条链路上 controller 回 8001、service 回 8003，本实现照搬这种不一致——
 * APP 侧已按此解析，统一码值属契约变更。</p>
 */
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

    /**
     * IF8A-11 请求支付信息成功。
     *
     * <p>{@code signType} 固定 {@code 00}、{@code sign} 固定空串——旧实现如此，
     * 即<b>本响应实际不签名</b>。这是既有形态，改成真签名属契约变更，需与 APP 侧同步。</p>
     */
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
     * @param payResult 只有 {@code SUCCESS} / {@code FAIL} 两种取值（<b>不是 FAILED</b>）
     * @param payDate   支付时间，<b>原样回吐、NEVER 把 null 兜成空串</b>。
     *                  两者旧实现都出现过、且分支不同：本地已终态时吐
     *                  {@code TBL_TVM_APP_ORDER.PAY_TIME} 列原值（失败单该列多为 <b>null</b>，
     *                  2026-09-13 用真实历史单 `SP20260911185631455135717` 重放实测旧回 null、
     *                  新曾兜成 {@code ""}）；向支付中心查到失败时才显式给 {@code ""}
     *                  （`AppOrderServiceImpl:277`）。
     *                  格式也随分支不同：终态吐 {@code yyyy-MM-dd HH:mm:ss}，查支付中心吐
     *                  {@code yyyyMMddHHmmss}，由调用方各自传入。
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
     * <p>{@code refundType} 固定 {@code 00}；{@code notifyUrl} 只在退款受理响应里出现，
     * 传 null 表示不下发该 key。</p>
     *
     * <p><b>{@code refundAmount} 必须序列化成字符串</b>：旧实现整条响应是
     * {@code Map<String, String>}，APP 侧拿到的一直是 {@code "400"} 而不是 {@code 400}。
     * 这里入参保持 {@code Long}（内部都是分），只在出参处转字符串，NEVER 直接 put 数值。</p>
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
