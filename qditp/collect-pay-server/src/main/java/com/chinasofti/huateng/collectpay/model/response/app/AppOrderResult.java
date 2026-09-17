package com.chinasofti.huateng.collectpay.model.response.app;

import com.alibaba.fastjson.JSONObject;
import com.chinasofti.huateng.collectpay.constant.AppCodeEnum;
import lombok.Data;

/** APP订单响应结果工具类。 */
@Data
public class AppOrderResult {

    private static final String RET_CODE = "retCode";
    private static final String RET_MSG = "retMsg";
    private static final String ORDER_NO = "orderNo";
    private static final String PAY_CHANNEL_CODE = "payChannelCode";
    private static final String PAYMENT_INFO = "paymentInfo";
    private static final String SIGN_TYPE = "signType";
    private static final String SIGN = "sign";
    private static final String TRADE_NO = "tradeNo";
    private static final String PAY_RESULT = "payResult";
    private static final String PAY_AMOUNT = "payAmount";
    private static final String PAY_DATE = "payDate";

    private static final String SUCCESS_CODE = "0000";
    private static final String SUCCESS_MSG = "成功";
    private static final String FAIL_CODE = "9999";
    private static final String FAIL_MSG = "失败";

    public static JSONObject success() {
        JSONObject result = new JSONObject();
        result.put(RET_CODE, SUCCESS_CODE);
        result.put(RET_MSG, SUCCESS_MSG);
        return result;
    }

    public static JSONObject success(String retMsg) {
        JSONObject result = new JSONObject();
        result.put(RET_CODE, SUCCESS_CODE);
        result.put(RET_MSG, retMsg);
        return result;
    }

    public static JSONObject successData(JSONObject jsonObject) {
        jsonObject.put("retCode", AppCodeEnum.SUCCESS.getCode());
        jsonObject.put("retMsg",AppCodeEnum.SUCCESS.getMsg());
        return jsonObject;
    }

    public static JSONObject successData(String orderNo) {
        JSONObject result = new JSONObject();
        result.put(RET_CODE, SUCCESS_CODE);
        result.put(RET_MSG, SUCCESS_MSG);
        result.put(ORDER_NO, orderNo);
        return result;
    }

    public static JSONObject successPayInfo(String payChannelCode, String paymentInfo, String signType, String sign) {
        JSONObject result = new JSONObject();
        result.put(RET_CODE, SUCCESS_CODE);
        result.put(RET_MSG, SUCCESS_MSG);
        result.put(PAY_CHANNEL_CODE, payChannelCode);
        result.put(PAYMENT_INFO, paymentInfo);
        result.put(SIGN_TYPE, signType);
        result.put(SIGN, sign);
        return result;
    }

    public static JSONObject successPayResult(String tradeNo, String payResult, String payAmount, String payDate) {
        JSONObject result = new JSONObject();
        result.put(RET_CODE, SUCCESS_CODE);
        result.put(RET_MSG, SUCCESS_MSG);
        result.put(TRADE_NO, tradeNo);
        result.put(PAY_RESULT, payResult);
        result.put(PAY_AMOUNT, payAmount);
        result.put(PAY_DATE, payDate);
        return result;
    }

    public static JSONObject fail(String retCode, String retMsg) {
        JSONObject result = new JSONObject();
        result.put(RET_CODE, retCode);
        result.put(RET_MSG, retMsg);
        return result;
    }

    public static JSONObject fail() {
        return fail(FAIL_CODE, FAIL_MSG);
    }

    public static JSONObject failMessage(String message) {
        return fail(FAIL_CODE, message);
    }

    public static JSONObject failData(JSONObject jsonObject) {
        jsonObject.put("retCode",AppCodeEnum.FAIL.getCode());
        jsonObject.put("retMsg",AppCodeEnum.FAIL.getMsg());
        return jsonObject;
    }
}