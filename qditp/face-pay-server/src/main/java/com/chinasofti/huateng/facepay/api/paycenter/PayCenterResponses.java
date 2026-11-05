package com.chinasofti.huateng.facepay.api.paycenter;

import com.alibaba.fastjson2.JSONObject;

public final class PayCenterResponses {

    public static final String CODE_SUCCESS = "0";

    public static final String CODE_FAIL = "-1";

    public static final String CODE_ORDER_NOT_EXIST = "2001";

    private PayCenterResponses() {
    }

    public static JSONObject success() {
        return body(CODE_SUCCESS, "成功");
    }

    public static JSONObject fail() {
        return body(CODE_FAIL, "失败/系统异常");
    }

    public static JSONObject orderNotExist() {
        return body(CODE_ORDER_NOT_EXIST, "订单不存在");
    }

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
