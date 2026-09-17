package com.chinasofti.huateng.collectpay.model.response.bom;

import com.alibaba.fastjson.JSONObject;

/** BOM非现金业务响应结果工具类。 */
public class BomOrderResult {

    /** 返回码字段名。 */
    private static final String RET_CODE = "retCode";

    /** 返回消息字段名。 */
    private static final String RET_MSG = "retMsg";

    /** 订单号字段名。 */
    private static final String ORDER_NO = "orderNo";

    /** 支付结果字段名。 */
    private static final String PAYMENT_RESULT = "paymentResult";

    /** 支付结果描述字段名。 */
    private static final String PAYMENT_RESULT_DESC = "paymentResultDesc";

    /** 状态描述字段名。 */
    private static final String MSG = "msg";

    /**
     * 成功响应（无数据）。
     *
     * @return 包含retCode=0000和retMsg=成功的响应对象
     */

    private static final String SUCCESS_CODE = "0000";
    private static final String SUCCESS_MSG = "成功";
    private static final String FAIL_CODE ="8999";
    private static final String FAIL_MSG = "失败";
    public static JSONObject success() {
        JSONObject result = new JSONObject();
        result.put(RET_CODE, SUCCESS_CODE);
        result.put(RET_MSG, SUCCESS_MSG);
        return result;
    }

    /**
     * 成功响应（带自定义消息）。
     *
     * @param retMsg 返回消息
     * @return 包含retCode=0000和自定义retMsg的响应对象
     */
    public static JSONObject success(String retMsg) {
        JSONObject result = new JSONObject();
        result.put(RET_CODE, SUCCESS_CODE);
        result.put(RET_MSG, retMsg);
        return result;
    }

    /**
     * 成功响应（带订单号）。
     *
     * @param orderNo 订单号
     * @return 包含retCode=0000、retMsg=成功和orderNo的响应对象
     */
    public static JSONObject successData(String orderNo) {
        JSONObject result = new JSONObject();
        result.put(RET_CODE, SUCCESS_CODE);
        result.put(RET_MSG, SUCCESS_MSG);
        result.put(ORDER_NO, orderNo);
        return result;
    }

    public static JSONObject successData(JSONObject result) {
        result.put(RET_CODE, SUCCESS_CODE);
        result.put(RET_MSG, SUCCESS_MSG);
        return result;
    }

    /**
     * 成功响应（带支付结果）。
     *
     * @param paymentResult    支付结果（SUCCESS/FAILED/PROCESSING）
     * @param paymentResultDesc 支付结果描述
     * @return 包含支付结果信息的响应对象
     */
    public static JSONObject successPaymentResult(String paymentResult, String paymentResultDesc) {
        JSONObject result = new JSONObject();
        result.put(RET_CODE, SUCCESS_CODE);
        result.put(RET_MSG, SUCCESS_MSG);
        result.put(PAYMENT_RESULT, paymentResult);
        result.put(PAYMENT_RESULT_DESC, paymentResultDesc);
        return result;
    }

    /**
     * 失败响应。
     *
     * @param retCode 返回码
     * @param retMsg  返回消息
     * @return 包含错误信息的响应对象
     */
    public static JSONObject fail(String retCode, String retMsg) {
        JSONObject result = new JSONObject();
        result.put(RET_CODE, retCode);
        result.put(RET_MSG, retMsg);
        return result;
    }

    /**
     * 默认失败响应。
     *
     * @return 默认失败响应对象
     */
    public static JSONObject fail() {
        return fail(FAIL_CODE, FAIL_MSG);
    }

    public static JSONObject failMessage(String message) {
        return fail(FAIL_CODE, message);
    }

    /**
     * 失败响应（带支付结果）。
     *
     * @param retCode          返回码
     * @param retMsg           返回消息
     * @param paymentResult    支付结果
     * @param paymentResultDesc 支付结果描述
     * @return 包含错误信息和支付结果的响应对象
     */
    public static JSONObject failData(JSONObject jsonObject) {
        jsonObject.put(RET_CODE, FAIL_CODE);
        jsonObject.put(RET_MSG, FAIL_MSG);
        return jsonObject;
    }

    /**
     * 成功响应（带支付结果和状态描述）。
     *
     * @param paymentResult    支付结果（SUCCESS/FAILED/PROCESSING）
     * @param paymentResultDesc 支付结果描述
     * @param msg              状态描述
     * @return 包含支付结果信息和状态描述的响应对象
     */
    public static JSONObject successPaymentResultWithMsg(String paymentResult, String paymentResultDesc, String msg) {
        JSONObject result = new JSONObject();
        result.put(RET_CODE, SUCCESS_CODE);
        result.put(RET_MSG, SUCCESS_MSG);
        result.put(PAYMENT_RESULT, paymentResult);
        result.put(PAYMENT_RESULT_DESC, paymentResultDesc);
        result.put(MSG, msg);
        return result;
    }
}