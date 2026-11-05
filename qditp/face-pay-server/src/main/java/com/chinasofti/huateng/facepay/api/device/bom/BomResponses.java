package com.chinasofti.huateng.facepay.api.device.bom;

import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.facepay.api.device.PaymentResult;

public final class BomResponses {

    public static final String CODE_SUCCESS = "0000";

    public static final String CODE_FAIL = "8999";

    public static final String CODE_ORDER_NO_ERROR = "8006";

    private BomResponses() {
    }

    public static JSONObject success() {
        return body(CODE_SUCCESS, "成功");
    }

    public static JSONObject successOrderNo(String orderNo) {
        JSONObject result = body(CODE_SUCCESS, "成功");
        result.put("orderNo", orderNo);
        return result;
    }

    public static JSONObject orderNoFail(String retCode, String retMsg) {
        JSONObject result = body(retCode, retMsg);
        result.put("orderNo", null);
        return result;
    }

    public static JSONObject paymentResult(PaymentResult result, String msg) {
        return paymentResultBody(body(CODE_SUCCESS, "成功"), result.getCode(), msg, msg);
    }

    public static JSONObject paymentResultFail(String retCode, String retMsg) {
        return paymentResultBody(body(retCode, retMsg), null, null, null);
    }

    private static JSONObject paymentResultBody(JSONObject body, String paymentResult,
                                                String paymentResultDesc, String msg) {
        body.put("paymentResult", paymentResult);
        body.put("paymentResultDesc", paymentResultDesc);
        body.put("msg", msg);
        return body;
    }

    public static JSONObject fail() {
        return body(CODE_FAIL, "失败");
    }

    public static JSONObject fail(String retCode, String retMsg) {
        return body(retCode, retMsg);
    }

    public static JSONObject failMessage(String retMsg) {
        return body(CODE_FAIL, retMsg);
    }

    public static JSONObject orderNotFound() {
        return body(CODE_ORDER_NO_ERROR, "订单号错误,没有找到匹配的订单");
    }

    public static JSONObject refundSuccess(String refundResult, String refundResultDesc, String refundNo) {
        return refundBody(body(CODE_SUCCESS, "成功"), refundResult, refundResultDesc, refundNo);
    }

    public static JSONObject refundFail(String retCode, String retMsg) {
        return refundBody(body(retCode, retMsg), null, null, null);
    }

    private static JSONObject refundBody(JSONObject body, String refundResult,
                                         String refundResultDesc, String refundNo) {
        body.put("refundResult", refundResult);
        body.put("refundResultDesc", refundResultDesc);
        body.put("refundNo", refundNo);
        return body;
    }

    public static JSONObject cardDataAnalyse(String providerId, String cardIssueDate, String msisdn,
                                             String cardId, String cardStatus, String lastLineCode,
                                             String lastStationCode, String lastUpdateDate,
                                             String lastTransAmount, String lastTicketTransSeq,
                                             Object adviceOpt, String managerCode, String transAmount) {
        return cardDataAnalyseBody(body(CODE_SUCCESS, "成功"), providerId, cardIssueDate, msisdn, cardId,
                cardStatus, lastLineCode, lastStationCode, lastUpdateDate, lastTransAmount,
                lastTicketTransSeq, adviceOpt, managerCode, transAmount);
    }

    public static JSONObject cardDataAnalyseFail(String retCode, String retMsg) {
        return cardDataAnalyseBody(body(retCode, retMsg), null, null, null, null, null, null, null,
                null, null, null, null, null, null);
    }

    private static JSONObject cardDataAnalyseBody(JSONObject body, String providerId, String cardIssueDate,
                                                  String msisdn, String cardId, String cardStatus,
                                                  String lastLineCode, String lastStationCode,
                                                  String lastUpdateDate, String lastTransAmount,
                                                  String lastTicketTransSeq, Object adviceOpt,
                                                  String managerCode, String transAmount) {
        body.put("providerId", providerId);
        body.put("cardIssueDate", cardIssueDate);
        body.put("msisdn", msisdn);
        body.put("cardId", cardId);
        body.put("cardStatus", cardStatus);
        body.put("lastLineCode", lastLineCode);
        body.put("lastStationCode", lastStationCode);
        body.put("lastUpdateDate", lastUpdateDate);
        body.put("lastTransAmout", lastTransAmount);
        body.put("lastTikcetTransSeq", lastTicketTransSeq);
        body.put("adviceOpt", adviceOpt);
        body.put("managerCode", managerCode);
        body.put("transAmount", transAmount);
        return body;
    }

    public static JSONObject cardDataUpdate(String cardData) {
        JSONObject body = body(CODE_SUCCESS, "成功");
        body.put("cardData", cardData);
        return body;
    }

    public static JSONObject cardDataUpdateFail(String retCode, String retMsg) {
        JSONObject body = body(retCode, retMsg);
        body.put("cardData", null);
        return body;
    }

    public static JSONObject qrCodeStatus(String itpUserId, String cardId, String lastTicketStatus,
                                          String lastHandleDateTime) {
        return qrCodeStatusBody(body(CODE_SUCCESS, "成功"), itpUserId, cardId, lastTicketStatus,
                lastHandleDateTime);
    }

    public static JSONObject qrCodeStatusFail(String retCode, String retMsg) {
        return qrCodeStatusBody(body(retCode, retMsg), null, null, null, null);
    }

    private static JSONObject qrCodeStatusBody(JSONObject body, String itpUserId, String cardId,
                                               String lastTicketStatus, String lastHandleDateTime) {
        body.put("itpUserId", itpUserId);
        body.put("cardId", cardId);
        body.put("lastTicketStatus", lastTicketStatus);
        body.put("lastHandleDateTime", lastHandleDateTime);
        return body;
    }

    public static JSONObject orderResult(String paymentResult, String paymentResultDesc, String orderNo,
                                         String transDate, Long transAmount, String paymentChannelCode) {
        return orderResultBody(body(CODE_SUCCESS, "成功"), paymentResult, paymentResultDesc, orderNo,
                transDate, transAmount == null ? null : String.valueOf(transAmount), paymentChannelCode);
    }

    public static JSONObject orderResultFail(String retCode, String retMsg) {
        return orderResultBody(body(retCode, retMsg), null, null, null, null, null, null);
    }

    private static JSONObject orderResultBody(JSONObject body, String paymentResult, String paymentResultDesc,
                                              String orderNo, String transDate, String transAmount,
                                              String paymentChannelCode) {
        body.put("paymentResult", paymentResult);
        body.put("paymentResultDesc", paymentResultDesc);
        body.put("orderNo", orderNo);
        body.put("transDate", transDate);
        body.put("transAmount", transAmount);
        body.put("paymentChannelCode", paymentChannelCode);
        return body;
    }

    private static JSONObject body(String retCode, String retMsg) {
        JSONObject result = new JSONObject();
        result.put("retCode", retCode);
        result.put("retMsg", retMsg);
        return result;
    }
}
