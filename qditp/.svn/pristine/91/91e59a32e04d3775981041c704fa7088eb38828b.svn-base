package com.chinasofti.huateng.collectpay.common;

import com.alibaba.fastjson.JSONObject;
import com.chinasofti.huateng.collectpay.constant.DevicePayCodeEnum;
import com.chinasofti.huateng.collectpay.constant.TvmPayCodeEnum;
import com.chinasofti.huateng.collectpay.entity.TvmPayOrder;
import com.chinasofti.huateng.collectpay.entity.TvmTakeTicketOrder;

public class DeviceResponse {

    // 拉码 返回给设备结果
    public static JSONObject getLaMaSuccessRespose(String orderNo, String payUrl){
        JSONObject jSONObject= new JSONObject();
        jSONObject.put("orderNo",orderNo);
        jSONObject.put("payUrl",payUrl);
        return jSONObject;
    }


    // 查询 返回结果 订单支付成功
    public static JSONObject getPaySuccessResult(String channel){
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("paymentResult", DevicePayCodeEnum.SUCCESS.getCode());
        jsonObject.put("paymentResultDesc",DevicePayCodeEnum.SUCCESS.getMsg());
        jsonObject.put("paymentChannelCode",channel);
        return jsonObject;
    }


    // 查询 返回结果 订单支付失败
    public static JSONObject getPayFailResult(String channel){
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("paymentResult",DevicePayCodeEnum.FAILED.getCode());
        jsonObject.put("paymentResultDesc",DevicePayCodeEnum.FAILED.getMsg());
        jsonObject.put("paymentChannelCode",channel);
        return jsonObject;
    }

    // 查询 返回结果 订单支付中
    public static JSONObject getPayIngResult(String channel){
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("paymentResult",DevicePayCodeEnum.ORDERED.getCode());
        jsonObject.put("paymentResultDesc",DevicePayCodeEnum.ORDERED.getMsg());
        jsonObject.put("paymentChannelCode",channel);
        return jsonObject;
    }


    // 出票故障通知 返回失败
    public static JSONObject getTakeTicketFaultFailResult(String retCode, String retMsg){
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("retCode", retCode);
        jsonObject.put("retMsg", retMsg);
        return jsonObject;
    }

    /**
     * 构建查询成功响应。
     */
    public static JSONObject getQuerySuccessResult(TvmPayOrder payOrder) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("orderNo", payOrder.getOrderNo());
        jsonObject.put("deviceId", payOrder.getDeviceId());
        jsonObject.put("entryStationCode", payOrder.getInStationCode());
        jsonObject.put("exitStationCode", payOrder.getOutStationCode());
        jsonObject.put("ticketPrice", payOrder.getTicketPrice());
        jsonObject.put("singelTicketNum", String.valueOf(payOrder.getTicketNum()));
        jsonObject.put("singleTicketType", payOrder.getTicketType());
        jsonObject.put("paymentChannelCode", payOrder.getChannel());
        return jsonObject;
    }

    /**
     * 构建查询失败响应。
     */
    public static JSONObject getQueryFailResult() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("orderNo", null);
        jsonObject.put("deviceId", null);
        jsonObject.put("entryStationCode", null);
        jsonObject.put("exitStationCode", null);
        jsonObject.put("ticketPrice", null);
        jsonObject.put("singelTicketNum", null);
        jsonObject.put("singleTicketType", null);
        jsonObject.put("paymentChannelCode", null);
        return jsonObject;
    }


    // 充值 返回给设备结果
    public static JSONObject getTopupSuccessRespose(String orderNo, String payUrl){
        JSONObject jSONObject= new JSONObject();
        jSONObject.put("orderNo",orderNo);
        jSONObject.put("payUrl",payUrl);
        return jSONObject;
    }

}
