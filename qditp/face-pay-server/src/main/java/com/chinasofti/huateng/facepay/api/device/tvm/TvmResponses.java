package com.chinasofti.huateng.facepay.api.device.tvm;

import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.facepay.api.device.DeviceRetCode;
import com.chinasofti.huateng.facepay.api.device.PaymentResult;
import com.chinasofti.huateng.facepay.support.F2fChannel;

/** TVM 响应报文组装。 */
public final class TvmResponses {

    private TvmResponses() {
    }

    /** 拉码下单成功：{@code retCode/retMsg + orderNo + payUrl}。 */
    public static JSONObject genSjtOrderSuccess(String orderNo, String payUrl) {
        return withRetCode(genSjtOrderBody(orderNo, payUrl), DeviceRetCode.SUCCESS);
    }

    /** IF2A-01 拉码下单失败，带与成功响应相同的 {@code orderNo} / {@code payUrl} 两键、值为 JSON null。 */
    public static JSONObject genSjtOrderFail(DeviceRetCode retCode, String retMsg) {
        return withRetCode(genSjtOrderBody(null, null), retCode, retMsg);
    }

    /** IF2A-01 拉码下单失败，文案取枚举默认值。 */
    public static JSONObject genSjtOrderFail(DeviceRetCode retCode) {
        return genSjtOrderFail(retCode, retCode.getMsg());
    }

    /** 下单类响应的两个业务键，拉码 / 充值的成功与失败四支共用，保证键名与键序恒定。 */
    private static JSONObject genSjtOrderBody(String orderNo, String payUrl) {
        JSONObject body = new JSONObject();
        body.put("orderNo", orderNo);
        body.put("payUrl", payUrl);
        return body;
    }

    /**
     * 查询支付结果。
     *
     * @param channel 支付渠道码，原样回吐（可能为 null，旧实现不做兜底）
     */
    public static JSONObject payResult(PaymentResult result, String channel) {
        JSONObject body = payResultBody(result.getCode(), result.getMsg(), channel);
        return withRetCode(body, result == PaymentResult.FAILED
                ? DeviceRetCode.FAIL : DeviceRetCode.SUCCESS);
    }

    /** IF2A-03 查询支付结果失败（参数缺失 / 订单不存在）， 带与成功响应相同的 3 个业务键、值为 JSON null。 */
    public static JSONObject payResultFail(DeviceRetCode retCode, String retMsg) {
        return withRetCode(payResultBody(null, null, null), retCode, retMsg);
    }

    /** 查询支付结果响应的 3 个业务键，成功与失败两支共用，保证键名与键序恒定。 */
    private static JSONObject payResultBody(String paymentResult, String paymentResultDesc, String channel) {
        JSONObject body = new JSONObject();
        body.put("paymentResult", paymentResult);
        body.put("paymentResultDesc", paymentResultDesc);
        body.put("paymentChannelCode", channel);
        return body;
    }

    /** 充值下单成功。 */
    public static JSONObject topupSuccess(String orderNo, String payUrl) {
        return genSjtOrderSuccess(orderNo, payUrl);
    }

    /** IF2A-09 充值下单失败，键集同 {@link #genSjtOrderFail(DeviceRetCode, String)}（充值与拉码同形态）。 */
    public static JSONObject topupFail(DeviceRetCode retCode, String retMsg) {
        return genSjtOrderFail(retCode, retMsg);
    }

    /** IF2A-09 充值下单失败，文案取枚举默认值。 */
    public static JSONObject topupFail(DeviceRetCode retCode) {
        return genSjtOrderFail(retCode);
    }

    /** 取票鉴权成功，8 个业务字段 + retCode/retMsg。 */
    public static JSONObject takeTicketAuthSuccess(String orderNo, String deviceId,
                                                  String entryStationCode, String exitStationCode,
                                                  Long ticketPrice, Integer ticketNum,
                                                  String singleTicketType, String paymentChannelCode) {
        JSONObject body = takeTicketAuthBody(orderNo, deviceId, entryStationCode, exitStationCode,
                ticketPrice, ticketNum, singleTicketType, paymentChannelCode);
        return withRetCode(body, DeviceRetCode.SUCCESS);
    }

    /** 取票鉴权「无激活的订单」，{@code retCode=2003} + 与成功响应完全相同的 8 个业务键、值全为 JSON null。 */
    public static JSONObject takeTicketAuthNoActiveOrder() {
        return takeTicketAuthFail(DeviceRetCode.NO_ACTIVE_ORDER, DeviceRetCode.NO_ACTIVE_ORDER.getMsg());
    }

    /** 取票鉴权的通用失败应答，键集同 {@link #takeTicketAuthSuccess}、业务值全为 JSON null。 */
    public static JSONObject takeTicketAuthFail(DeviceRetCode retCode, String retMsg) {
        JSONObject body = takeTicketAuthBody(null, null, null, null, null, null, null, null);
        return withRetCode(body, retCode, retMsg);
    }

    /** 取票鉴权响应的 8 个业务键，成功与「无激活的订单」两支共用，保证键名与键序恒定。 */
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

    /**
     * 支付中心查询 ITP 订单详情，13 个 key 逐字照搬旧 {@code getPayCenterPayOrderDetailResult}。
     *
     * @param orderStatus 旧口径：{@code 1} 支付中、{@code 2} 支付成功、{@code 7} 已退款、其余为空串
     * @param payDate     仅支付成功时有值，格式 yyyyMMddHHmmss；其余传 null 表示不放该 key
     */
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

    /**
     * 充值单的订单详情，键集与购票单**不同**，逐字照搬旧 {@code TvmTopupServiceImpl.getPayCenterPayOrderDetailResult}：
     *
     * @param deviceId 下单设备号，长度不足 5 位时站码为空串（照搬旧判据）
     */
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

    /** 只有 {@code retCode/retMsg} 的成功响应。 */
    public static JSONObject success() {
        return withRetCode(new JSONObject(), DeviceRetCode.SUCCESS);
    }

    /**
     * IF2A 设备退款成功响应，{@code retCode=0000} + 3 个业务键。
     *
     * @param refundResult     {@code PROCESSING} / {@code SUCCESS} / {@code FAILED}
     * @param refundResultDesc 结果描述
     * @param refundNo         我方退款单号
     */
    public static JSONObject refundSuccess(String refundResult, String refundResultDesc, String refundNo) {
        return withRetCode(refundBody(refundResult, refundResultDesc, refundNo), DeviceRetCode.SUCCESS);
    }

    /** 退款接口专用失败响应，{@code retCode=9999}。 */
    public static JSONObject refundFail(String retMsg) {
        JSONObject body = refundBody(null, null, null);
        body.put("retCode", "9999");
        body.put("retMsg", retMsg);
        return body;
    }

    /** 退款响应的 3 个业务键，成功与失败共用，保证键名与键序恒定。 */
    private static JSONObject refundBody(String refundResult, String refundResultDesc, String refundNo) {
        JSONObject body = new JSONObject();
        body.put("refundResult", refundResult);
        body.put("refundResultDesc", refundResultDesc);
        body.put("refundNo", refundNo);
        return body;
    }

    /** 出票上报「订单不存在」的文案，两个上报接口共用同一句，逐字照搬旧实现。 */
    private static final String ORDER_NOT_FOUND_MSG = "没有找到匹配的订单，请确认订单号是否正确";

    /** 设备退款「订单不存在」应答，{@code retCode=2002}、文案同 {@link #ORDER_NOT_FOUND_MSG}。 */
    public static JSONObject refundOrderNotFound() {
        return withRetCode(refundBody(null, null, null),
                DeviceRetCode.INVALID_PARAM, ORDER_NOT_FOUND_MSG);
    }

    /** 出票成功上报的「订单不存在」应答，按 {@code providerId} 分两个码： {@code providerId=03} 回 {@code 2999}，其余（含缺失）回 {@code -1}。 */
    public static JSONObject takeTicketResultOrderNotFound(String providerId) {
        JSONObject body = new JSONObject();
        body.put("retCode", F2fChannel.BOM.equals(providerId) ? "2999" : "-1");
        body.put("retMsg", ORDER_NOT_FOUND_MSG);
        return body;
    }

    /** 出票失败上报的「订单不存在」应答，{@code retCode=2999}。 */
    public static JSONObject takeTicketFailResultOrderNotFound() {
        JSONObject body = new JSONObject();
        body.put("retCode", "2999");
        body.put("retMsg", ORDER_NOT_FOUND_MSG);
        return body;
    }

    /** 失败响应，带指定错误码与自定义文案。 */
    public static JSONObject fail(DeviceRetCode retCode, String retMsg) {
        JSONObject body = new JSONObject();
        body.put("retCode", retCode.getCode());
        body.put("retMsg", retMsg);
        return body;
    }

    /** 失败响应，文案取枚举默认值。 */
    public static JSONObject fail(DeviceRetCode retCode) {
        return fail(retCode, retCode.getMsg());
    }

    private static JSONObject withRetCode(JSONObject body, DeviceRetCode retCode) {
        return withRetCode(body, retCode, retCode.getMsg());
    }

    /** 把 {@code retCode/retMsg} 追加到已组装好的业务体末尾。 */
    private static JSONObject withRetCode(JSONObject body, DeviceRetCode retCode, String retMsg) {
        body.put("retCode", retCode.getCode());
        body.put("retMsg", retMsg);
        return body;
    }
}
