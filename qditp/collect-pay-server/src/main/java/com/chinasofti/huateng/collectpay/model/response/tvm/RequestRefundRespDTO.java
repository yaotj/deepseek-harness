package com.chinasofti.huateng.collectpay.model.response.tvm;

import com.alibaba.fastjson.JSONObject;

/** 退款响应DTO。 */
public class RequestRefundRespDTO {

    /** 返回码。 */
    private String retCode;

    /** 返回消息。 */
    private String retMsg;

    /** 退款结果。 */
    private String refundResult;

    /** 退款结果描述。 */
    private String refundResultDesc;

    /** 退款单号。 */
    private String refundNo;

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

    public String getRefundResult() {
        return refundResult;
    }

    public void setRefundResult(String refundResult) {
        this.refundResult = refundResult;
    }

    public String getRefundResultDesc() {
        return refundResultDesc;
    }

    public void setRefundResultDesc(String refundResultDesc) {
        this.refundResultDesc = refundResultDesc;
    }

    public String getRefundNo() {
        return refundNo;
    }

    public void setRefundNo(String refundNo) {
        this.refundNo = refundNo;
    }

    public static JSONObject success(String refundResult, String refundResultDesc, String refundNo) {
        JSONObject json = new JSONObject();
        json.put("retCode", "0000");
        json.put("retMsg", "成功");
        json.put("refundResult", refundResult);
        json.put("refundResultDesc", refundResultDesc);
        json.put("refundNo", refundNo);
        return json;
    }

    public static JSONObject fail(String retCode, String retMsg) {
        JSONObject json = new JSONObject();
        json.put("retCode", retCode);
        json.put("retMsg", retMsg);
        return json;
    }
}
