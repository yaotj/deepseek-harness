package com.chinasofti.huateng.facepay.api.device.app;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

public final class AppResponses {

    public static final String CODE_SUCCESS = "0000";

    public static final String CODE_INVALID_ORDER_PARAM = "8001";

    public static final String CODE_INVALID_PARAM = "8003";

    public static final String CODE_PAYING = "8999";

    public static final String CODE_FAIL = "9999";

    private AppResponses() {
    }

    public static JSONObject success() {
        return body(CODE_SUCCESS, "成功");
    }

    public static JSONObject orderNo(String orderNo) {
        JSONObject result = body(CODE_SUCCESS, "成功");
        result.put("orderNo", orderNo);
        return result;
    }

    public static JSONObject payInfo(String payChannelCode, String paymentInfo) {
        JSONObject result = body(CODE_SUCCESS, "成功");
        result.put("payChannelCode", payChannelCode);
        result.put("paymentInfo", paymentInfo);
        result.put("signType", "00");
        result.put("sign", "");
        return result;
    }

    public static JSONObject payResult(String tradeNo, String payResult, Long payAmount, String payDate) {
        JSONObject result = body(CODE_SUCCESS, "成功");
        result.put("tradeNo", tradeNo);
        result.put("payResult", payResult);
        result.put("payAmount", payAmount == null ? null : String.valueOf(payAmount));
        result.put("payDate", payDate);
        return result;
    }

    public static JSONObject orderList(JSONArray orderList) {
        JSONObject result = body(CODE_SUCCESS, "成功");
        result.put("orderList", orderList);
        return result;
    }

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

    public static JSONObject fail(String retCode, String retMsg) {
        return body(retCode, retMsg);
    }

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
