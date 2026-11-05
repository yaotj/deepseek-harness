package com.chinasofti.huateng.facepay.api.device.tvm;

import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.facepay.api.device.DeviceRetCode;
import com.chinasofti.huateng.facepay.api.device.PaymentResult;
import com.chinasofti.huateng.facepay.support.F2fChannel;

public final class TvmResponses {

    private TvmResponses() {
    }

    public static JSONObject genSjtOrderSuccess(String orderNo, String payUrl) {
        return withRetCode(genSjtOrderBody(orderNo, payUrl), DeviceRetCode.SUCCESS);
    }

    public static JSONObject genSjtOrderFail(DeviceRetCode retCode, String retMsg) {
        return withRetCode(genSjtOrderBody(null, null), retCode, retMsg);
    }

    public static JSONObject genSjtOrderFail(DeviceRetCode retCode) {
        return genSjtOrderFail(retCode, retCode.getMsg());
    }

    private static JSONObject genSjtOrderBody(String orderNo, String payUrl) {
        JSONObject body = new JSONObject();
        body.put("orderNo", orderNo);
        body.put("payUrl", payUrl);
        return body;
    }

    public static JSONObject payResult(PaymentResult result, String channel) {
        JSONObject body = payResultBody(result.getCode(), result.getMsg(), channel);
        return withRetCode(body, result == PaymentResult.FAILED
                ? DeviceRetCode.FAIL : DeviceRetCode.SUCCESS);
    }

    public static JSONObject payResultFail(DeviceRetCode retCode, String retMsg) {
        return withRetCode(payResultBody(null, null, null), retCode, retMsg);
    }

    private static JSONObject payResultBody(String paymentResult, String paymentResultDesc, String channel) {
        JSONObject body = new JSONObject();
        body.put("paymentResult", paymentResult);
        body.put("paymentResultDesc", paymentResultDesc);
        body.put("paymentChannelCode", channel);
        return body;
    }

    public static JSONObject topupSuccess(String orderNo, String payUrl) {
        return genSjtOrderSuccess(orderNo, payUrl);
    }

    public static JSONObject topupFail(DeviceRetCode retCode, String retMsg) {
        return genSjtOrderFail(retCode, retMsg);
    }

    public static JSONObject topupFail(DeviceRetCode retCode) {
        return genSjtOrderFail(retCode);
    }

    public static JSONObject takeTicketAuthSuccess(String orderNo, String deviceId,
                                                  String entryStationCode, String exitStationCode,
                                                  Long ticketPrice, Integer ticketNum,
                                                  String singleTicketType, String paymentChannelCode) {
        JSONObject body = takeTicketAuthBody(orderNo, deviceId, entryStationCode, exitStationCode,
                ticketPrice, ticketNum, singleTicketType, paymentChannelCode);
        return withRetCode(body, DeviceRetCode.SUCCESS);
    }

    public static JSONObject takeTicketAuthNoActiveOrder() {
        return takeTicketAuthFail(DeviceRetCode.NO_ACTIVE_ORDER, DeviceRetCode.NO_ACTIVE_ORDER.getMsg());
    }

    public static JSONObject takeTicketAuthFail(DeviceRetCode retCode, String retMsg) {
        JSONObject body = takeTicketAuthBody(null, null, null, null, null, null, null, null);
        return withRetCode(body, retCode, retMsg);
    }

    private static JSONObject takeTicketAuthBody(String orderNo, String deviceId,
                                                 String entryStationCode, String exitStationCode,
                                                 Long ticketPrice, Integer ticketNum,
                                                 String singleTicketType, String paymentChannelCode) {
        JSONObject body = new JSONObject();
        body.put("orderNo", orderNo);
        body.put("deviceId", deviceId);
        body.put("entryStationCode", entryStationCode);
        body.put("exitStationCode", exitStationCode);
        body.put("ticketPrice", ticketPrice);
        body.put("singelTicketNum", ticketNum == null ? null : String.valueOf(ticketNum));
        body.put("singleTicketType", singleTicketType);
        body.put("paymentChannelCode", paymentChannelCode);
        return body;
    }

    public static JSONObject payOrderDetail(String orderNo, String entryStationCode, String exitStationCode,
                                            Integer ticketNum, Long totalTicketPrice, String regDate,
                                            String orderStatus, String payDate, String notifyUrl) {
        JSONObject body = new JSONObject();
        body.put("orderNo", orderNo);
        body.put("singlePickupStationName", entryStationCode);
        body.put("singlePickupStationCode", entryStationCode);
        body.put("singleGetoffStationName", exitStationCode);
        body.put("singleGetoffStationCode", exitStationCode);
        body.put("singleTicketNum", ticketNum);
        body.put("totalTicketPrice", totalTicketPrice == null ? null : String.valueOf(totalTicketPrice));
        body.put("regDate", regDate);
        if (payDate != null) {
            body.put("payDate", payDate);
        }
        body.put("orderStatus", orderStatus);
        body.put("subject", "一票通_单程票");
        body.put("body", "一票通_单程票");
        body.put("notifyUrl", notifyUrl);
        return withRetCode(body, DeviceRetCode.SUCCESS);
    }

    public static JSONObject topupOrderDetail(String orderNo, String deviceId, Long transAmount,
                                              String regDate, String orderStatus, String payDate,
                                              String notifyUrl) {
        String stationCode = deviceId == null || deviceId.length() <= 4 ? "" : deviceId.substring(0, 4);
        String amount = transAmount == null ? null : String.valueOf(transAmount);
        JSONObject body = new JSONObject();
        body.put("orderNo", orderNo);
        body.put("singlePickupStationName", stationCode);
        body.put("singlePickupStationCode", stationCode);
        body.put("singleTicketNum", "1");
        body.put("singleTicketPrice", amount);
        body.put("totalTicketPrice", amount);
        body.put("regDate", regDate);
        if (payDate != null) {
            body.put("payDate", payDate);
        }
        body.put("orderStatus", orderStatus);
        body.put("subject", "一票通_单程票");
        body.put("body", "一票通_单程票");
        body.put("notifyUrl", notifyUrl);
        return withRetCode(body, DeviceRetCode.SUCCESS);
    }

    public static JSONObject success() {
        return withRetCode(new JSONObject(), DeviceRetCode.SUCCESS);
    }

    public static JSONObject refundSuccess(String refundResult, String refundResultDesc, String refundNo) {
        return withRetCode(refundBody(refundResult, refundResultDesc, refundNo), DeviceRetCode.SUCCESS);
    }

    public static JSONObject refundFail(String retMsg) {
        JSONObject body = refundBody(null, null, null);
        body.put("retCode", "9999");
        body.put("retMsg", retMsg);
        return body;
    }

    private static JSONObject refundBody(String refundResult, String refundResultDesc, String refundNo) {
        JSONObject body = new JSONObject();
        body.put("refundResult", refundResult);
        body.put("refundResultDesc", refundResultDesc);
        body.put("refundNo", refundNo);
        return body;
    }

    private static final String ORDER_NOT_FOUND_MSG = "没有找到匹配的订单，请确认订单号是否正确";

    public static JSONObject refundOrderNotFound() {
        return withRetCode(refundBody(null, null, null),
                DeviceRetCode.INVALID_PARAM, ORDER_NOT_FOUND_MSG);
    }

    public static JSONObject takeTicketResultOrderNotFound(String providerId) {
        JSONObject body = new JSONObject();
        body.put("retCode", F2fChannel.BOM.equals(providerId) ? "2999" : "-1");
        body.put("retMsg", ORDER_NOT_FOUND_MSG);
        return body;
    }

    public static JSONObject takeTicketFailResultOrderNotFound() {
        JSONObject body = new JSONObject();
        body.put("retCode", "2999");
        body.put("retMsg", ORDER_NOT_FOUND_MSG);
        return body;
    }

    public static JSONObject fail(DeviceRetCode retCode, String retMsg) {
        JSONObject body = new JSONObject();
        body.put("retCode", retCode.getCode());
        body.put("retMsg", retMsg);
        return body;
    }

    public static JSONObject fail(DeviceRetCode retCode) {
        return fail(retCode, retCode.getMsg());
    }

    private static JSONObject withRetCode(JSONObject body, DeviceRetCode retCode) {
        return withRetCode(body, retCode, retCode.getMsg());
    }

    private static JSONObject withRetCode(JSONObject body, DeviceRetCode retCode, String retMsg) {
        body.put("retCode", retCode.getCode());
        body.put("retMsg", retMsg);
        return body;
    }
}
