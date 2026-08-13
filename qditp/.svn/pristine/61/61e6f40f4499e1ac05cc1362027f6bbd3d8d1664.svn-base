package com.chinasofti.huateng.collectpay.model.response.tvm;

import com.alibaba.fastjson.JSONObject;

/**
 * IF2A-11 扫码支付应答报文（ITP -> TVM）。
 */
public class RequestPaymentRespDTO {

    /**
     * 返回码。
     */
    private String retCode;

    /**
     * 返回消息。
     */
    private String retMsg;

    /**
     * 支付结果。
     * 成功：SUCCESS
     * 失败：FAILED
     */
    private String paymentResult;

    /**
     * 支付结果描述。
     */
    private String paymentResultDesc;

    public String getRetCode() {
        return retCode;
    }

    public void setRetCode(String retCode) {
        this.retCode = retCode;
    }

    public String getRetMsg() {
        return retMsg;
    }

    public void setRetMsg(String retMsg) {
        this.retMsg = retMsg;
    }

    public String getPaymentResult() {
        return paymentResult;
    }

    public void setPaymentResult(String paymentResult) {
        this.paymentResult = paymentResult;
    }

    public String getPaymentResultDesc() {
        return paymentResultDesc;
    }

    public void setPaymentResultDesc(String paymentResultDesc) {
        this.paymentResultDesc = paymentResultDesc;
    }

    public static JSONObject success(String paymentResult, String paymentResultDesc) {
        JSONObject json = new JSONObject();
        json.put("retCode", "0000");
        json.put("retMsg", "成功");
        json.put("paymentResult", paymentResult);
        json.put("paymentResultDesc", paymentResultDesc);
        return json;
    }

    public static JSONObject fail(String retCode, String retMsg, String paymentResult, String paymentResultDesc) {
        JSONObject json = new JSONObject();
        json.put("retCode", retCode);
        json.put("retMsg", retMsg);
        json.put("paymentResult", paymentResult);
        json.put("paymentResultDesc", paymentResultDesc);
        return json;
    }
}
