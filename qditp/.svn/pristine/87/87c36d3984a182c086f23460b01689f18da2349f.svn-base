package com.chinasofti.huateng.collectpay.model.response.tvm;

import com.alibaba.fastjson.JSONObject;
import com.chinasofti.huateng.collectpay.constant.TvmPayCodeEnum;

/**
 * IF2A-01 提交单程票订单应答报文（ITP -> TVM）。
 */
public class TvmOrderResult {
    /**
     * 返回码。
     */
    private String retCode;

    /**
     * 返回消息。
     */
    private String retMsg;

    /**
     * 订单号。
     */
    private String orderNo;

    /**
     * 二维码信息（支付URL），TVM把整个字段显示为二维码。
     */
    private String payUrl;

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

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getPayUrl() {
        return payUrl;
    }

    public void setPayUrl(String payUrl) {
        this.payUrl = payUrl;
    }

    public static JSONObject success() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("retCode",TvmPayCodeEnum.SUCCESS.getCode());
        jsonObject.put("retMsg",TvmPayCodeEnum.SUCCESS.getMsg());
        return jsonObject;
    }

    public static JSONObject successData(JSONObject jsonObject) {
        jsonObject.put("retCode",TvmPayCodeEnum.SUCCESS.getCode());
        jsonObject.put("retMsg",TvmPayCodeEnum.SUCCESS.getMsg());
        return jsonObject;
    }

    public static JSONObject failData(JSONObject jsonObject) {
        jsonObject.put("retCode",TvmPayCodeEnum.FAIL.getCode());
        jsonObject.put("retMsg",TvmPayCodeEnum.FAIL.getMsg());
        return jsonObject;
    }

    public static JSONObject fail() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("retCode",TvmPayCodeEnum.FAIL.getCode());
        jsonObject.put("retMsg",TvmPayCodeEnum.FAIL.getMsg());
        return jsonObject;
    }

    public static JSONObject fail(String retCode,String retMsg) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("retCode",retCode);
        jsonObject.put("retMsg",retMsg);
        return jsonObject;
    }
    @Override
    public String toString() {
        return "RequestGenSjtOrderRespDTO{" +
                "retCode='" + retCode + '\'' +
                ", retMsg='" + retMsg + '\'' +
                ", orderNo='" + orderNo + '\'' +
                ", payUrl='" + payUrl + '\'' +
                '}';
    }
}
