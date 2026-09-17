package com.chinasofti.huateng.facepay.api.device.bom;

import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.facepay.api.device.PaymentResult;

/** BOM 响应报文组装，逐字照搬旧 {@code BomOrderResult}。 */
public final class BomResponses {

    /** 成功码，与 TVM 的 {@code 0000} 一致。 */
    public static final String CODE_SUCCESS = "0000";

    /** 失败码，8999，不是 TVM 的 2999。 */
    public static final String CODE_FAIL = "8999";

    /** 订单号错误 / 订单不存在，见 {@link #orderNotFound()}。 */
    public static final String CODE_ORDER_NO_ERROR = "8006";

    private BomResponses() {
    }

    /** 只有 {@code retCode/retMsg} 的成功响应。 */
    public static JSONObject success() {
        return body(CODE_SUCCESS, "成功");
    }

    /** 下单成功：{@code retCode/retMsg + orderNo}。 */
    public static JSONObject successOrderNo(String orderNo) {
        JSONObject result = body(CODE_SUCCESS, "成功");
        result.put("orderNo", orderNo);
        return result;
    }

    /** 下单失败，带与 {@link #successOrderNo} 相同的 {@code orderNo} 键、值为 JSON null。 */
    public static JSONObject orderNoFail(String retCode, String retMsg) {
        JSONObject result = body(retCode, retMsg);
        result.put("orderNo", null);
        return result;
    }

    /** 支付结果响应：{@code retCode/retMsg + paymentResult + paymentResultDesc + msg}。 */
    public static JSONObject paymentResult(PaymentResult result, String msg) {
        return paymentResultBody(body(CODE_SUCCESS, "成功"), result.getCode(), msg, msg);
    }

    /** 支付结果响应的失败形态，带与 {@link #paymentResult} 相同的 3 个业务键、值为 JSON null。 */
    public static JSONObject paymentResultFail(String retCode, String retMsg) {
        return paymentResultBody(body(retCode, retMsg), null, null, null);
    }

    /** 支付结果响应的 3 个业务键，成功与失败两支共用，保证键名与键序恒定。 */
    private static JSONObject paymentResultBody(JSONObject body, String paymentResult,
                                                String paymentResultDesc, String msg) {
        body.put("paymentResult", paymentResult);
        body.put("paymentResultDesc", paymentResultDesc);
        body.put("msg", msg);
        return body;
    }

    /** 失败响应，文案固定「失败」。 */
    public static JSONObject fail() {
        return body(CODE_FAIL, "失败");
    }

    /** 失败响应，带指定错误码。 */
    public static JSONObject fail(String retCode, String retMsg) {
        return body(retCode, retMsg);
    }

    /** 失败响应，带自定义文案（旧 {@code failMessage}）。 */
    public static JSONObject failMessage(String retMsg) {
        return body(CODE_FAIL, retMsg);
    }

    /** 「订单不存在」专用响应，{@code retCode} 是 8006 而不是 8999。 */
    public static JSONObject orderNotFound() {
        return body(CODE_ORDER_NO_ERROR, "订单号错误,没有找到匹配的订单");
    }

    /** BOM 单程票退款成功响应，{@code retCode=0000} + 3 个业务键（2026-09-16 新增）。 */
    public static JSONObject refundSuccess(String refundResult, String refundResultDesc, String refundNo) {
        return refundBody(body(CODE_SUCCESS, "成功"), refundResult, refundResultDesc, refundNo);
    }

    /** BOM 单程票退款失败，带与 {@link #refundSuccess} 相同的 3 个业务键、值为 JSON null。 */
    public static JSONObject refundFail(String retCode, String retMsg) {
        return refundBody(body(retCode, retMsg), null, null, null);
    }

    /** 退款响应的 3 个业务键，BOM 侧成功与失败共用，保证键名与键序恒定。 */
    private static JSONObject refundBody(JSONObject body, String refundResult,
                                         String refundResultDesc, String refundNo) {
        body.put("refundResult", refundResult);
        body.put("refundResultDesc", refundResultDesc);
        body.put("refundNo", refundNo);
        return body;
    }

    /** IF5A-01 票卡分析结果，14 个业务 key 逐字照搬旧 {@code RequestCardDataAnalyseRespDTO}。 */
    public static JSONObject cardDataAnalyse(String providerId, String cardIssueDate, String msisdn,
                                             String cardId, String cardStatus, String lastLineCode,
                                             String lastStationCode, String lastUpdateDate,
                                             String lastTransAmount, String lastTicketTransSeq,
                                             Object adviceOpt, String managerCode, String transAmount) {
        return cardDataAnalyseBody(body(CODE_SUCCESS, "成功"), providerId, cardIssueDate, msisdn, cardId,
                cardStatus, lastLineCode, lastStationCode, lastUpdateDate, lastTransAmount,
                lastTicketTransSeq, adviceOpt, managerCode, transAmount);
    }

    /** IF5A-01 票卡分析失败，带与 {@link #cardDataAnalyse} 相同的 13 个业务键、值为 JSON null。 */
    public static JSONObject cardDataAnalyseFail(String retCode, String retMsg) {
        return cardDataAnalyseBody(body(retCode, retMsg), null, null, null, null, null, null, null,
                null, null, null, null, null, null);
    }

    /** 票卡分析响应的 13 个业务键，成功与失败两支共用，保证键名（含两个拼写错误）与键序恒定。 */
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

    /** IF5A-03 票卡更新结果：{@code retCode/retMsg + cardData}。 */
    public static JSONObject cardDataUpdate(String cardData) {
        JSONObject body = body(CODE_SUCCESS, "成功");
        body.put("cardData", cardData);
        return body;
    }

    /** IF5A-03 票卡更新失败，带与 {@link #cardDataUpdate} 相同的 {@code cardData} 键、值为 JSON null。 */
    public static JSONObject cardDataUpdateFail(String retCode, String retMsg) {
        JSONObject body = body(retCode, retMsg);
        body.put("cardData", null);
        return body;
    }

    /** 单程票交易查询结果，8 个 key 照搬旧实现。 */
    public static JSONObject orderResult(String paymentResult, String paymentResultDesc, String orderNo,
                                         String transDate, Long transAmount, String paymentChannelCode) {
        return orderResultBody(body(CODE_SUCCESS, "成功"), paymentResult, paymentResultDesc, orderNo,
                transDate, transAmount == null ? null : String.valueOf(transAmount), paymentChannelCode);
    }

    /** 单程票交易查询失败，带与 {@link #orderResult} 相同的 6 个业务键、值为 JSON null。 */
    public static JSONObject orderResultFail(String retCode, String retMsg) {
        return orderResultBody(body(retCode, retMsg), null, null, null, null, null, null);
    }

    /** 单程票交易查询响应的 6 个业务键，成功与失败两支共用，保证键名与键序恒定。 */
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
