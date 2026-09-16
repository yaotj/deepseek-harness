package com.chinasofti.huateng.facepay.api.device.bom;

import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.facepay.api.device.PaymentResult;

/**
 * BOM 响应报文组装，逐字照搬旧 {@code BomOrderResult}。
 *
 * <p><b>错误码族与 TVM 不同：成功 {@code 0000}、失败 {@code 8999}</b>（不是 TVM 的 2999）。
 * 这一族同时被 TVM 的 {@code requestPayment} 使用——旧实现里 TVM 付款码支付复用了
 * BOM 的这段实现，校验失败也回 8999。看着像 bug，但设备侧已按此解析，属既有契约，NEVER 统一。</p>
 *
 * <p><b>2026-09-16 起，凡是「成功响应带业务字段」的接口，其失败分支 MUST 也回同一套键、值全为
 * JSON null</b>（用户明确要求：「参考 requestTakeTicketAuth，错误也要有全量字段，保证设备端能正常编译」，
 * 落地口径与 {@code TvmResponses} 一致）。BOM 侧按固定结构体解析，键缺失即解析失败。
 * 形态是每类响应各抽一个私有 {@code *Body(...)}，成功与失败共用，
 * <b>NEVER 在失败方法里再抄一份 put 列表</b>，否则以后改成功分支的键会漏掉失败支。</p>
 *
 * <p><b>{@link #fail()} / {@link #fail(String, String)} / {@link #failMessage(String)} /
 * {@link #orderNotFound()} 这四个两键构造 NEVER 删、NEVER 加业务字段</b>：成功侧本来就只有
 * {@code retCode/retMsg} 的接口（设备心跳、业务操作结果通知、充值结果通知、HCE 更新结果通知）
 * 仍在用它们，给那些接口补业务字段反而是契约变更。
 * <b>单程票退款（{@code requestTicketRefund}）已于 2026-09-16 移出这份名单</b> ——
 * 它现在回 3 个业务键，见 {@link #refundSuccess}，本行此前把它列在这里已作废、NEVER 回退。</p>
 */
public final class BomResponses {

    /** 成功码，与 TVM 的 {@code 0000} 一致。 */
    public static final String CODE_SUCCESS = "0000";

    /** 失败码，<b>8999，不是 TVM 的 2999</b>。 */
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

    /**
     * 下单失败，<b>带与 {@link #successOrderNo} 相同的 {@code orderNo} 键、值为 JSON null</b>。
     *
     * <p>用于 IF8A-04 非现金收款下单的全部失败分支。{@code retCode} 由调用方给定：
     * 入参校验 {@code 8003}、业务失败 {@code 8999}，<b>NEVER 借这次改动统一</b>。</p>
     */
    public static JSONObject orderNoFail(String retCode, String retMsg) {
        JSONObject result = body(retCode, retMsg);
        result.put("orderNo", null);
        return result;
    }

    /**
     * 支付结果响应：{@code retCode/retMsg + paymentResult + paymentResultDesc + msg}。
     *
     * <p>{@code paymentResultDesc} 与 {@code msg} <b>取同一个字符串</b>，逐字照搬旧
     * {@code BomOrderResult.successPaymentResultWithMsg(paymentResult, desc, msg)} 的调用形态
     * ——旧实现两个入参传的是同一个值。这里 NEVER 改成 {@code result.getMsg()}：那会把
     * {@code paymentResultDesc} 变成枚举里的「失败 / 成功」，与旧服务的「支付失败 / 支付成功」
     * 不一致（2026-09-11 双跑对比实测到）。</p>
     */
    public static JSONObject paymentResult(PaymentResult result, String msg) {
        return paymentResultBody(body(CODE_SUCCESS, "成功"), result.getCode(), msg, msg);
    }

    /**
     * 支付结果响应的失败形态，<b>带与 {@link #paymentResult} 相同的 3 个业务键、值为 JSON null</b>。
     *
     * <p>用于 IF8A-05 扫码支付 / IF8A-06 查询支付结果的入参校验、订单不存在、订单状态不允许支付
     * 这几支 —— 它们此前只回 {@code retCode/retMsg}。</p>
     *
     * <p><b>业务值 MUST 全 null，NEVER 填成 {@code FAILED}</b>：这几支的语义是「这笔请求不成立」，
     * 不是「这笔支付失败了」。真正的支付失败仍走 {@code paymentResult(FAILED, "支付失败")} 且
     * {@code retCode} 是 {@code 0000} —— 那是 BOM 已按此解析的既有形态，两者 NEVER 混用。</p>
     */
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

    /**
     * 失败响应，带指定错误码。BOM 族的码值见旧 {@code BomPayCodeEnum}：
     * {@code 8001} 非法设备、{@code 8002} 订单未支付、{@code 8003} 非法参数、
     * {@code 8004} 无激活的订单、{@code 8005} 充值金额超限、{@code 8006} 订单号错误、
     * {@code 8007} 订单已退款。旧实现只实际用了 {@code 8003} 与 {@code 8006}。
     */
    public static JSONObject fail(String retCode, String retMsg) {
        return body(retCode, retMsg);
    }

    /** 失败响应，带自定义文案（旧 {@code failMessage}）。 */
    public static JSONObject failMessage(String retMsg) {
        return body(CODE_FAIL, retMsg);
    }

    /**
     * 「订单不存在」专用响应，<b>{@code retCode} 是 8006 而不是 8999</b>。
     *
     * <p>2026-09-11 用 BOM 自检报文（{@code orderNo} 二十个 0）双跑实测：旧应用
     * {@code requestPayment} / {@code requestGetPayResult} / {@code notiBusResult}
     * 三处都回 {@code 8006}，新实现原先一律回 {@code failMessage}（8999），
     * <b>设备侧按 8006 分支处理「订单号打错了」，收到 8999 会当成通用失败</b>。</p>
     *
     * <p><b>唯一例外是 {@code requestTicketRefund}：旧实现在那里回 {@code 9999}</b>
     * （BOM 域仅此一处），NEVER 把它也改成 8006——见
     * {@code BomOrderServiceImpl.requestTicketTRefund}。</p>
     *
     * <p><b>但那个 9999 只存在于旧代码的字面量里、运行时走不到</b>（2026-09-16 双跑实测，ADR-D112 续（二））：
     * 用不存在的 {@code orderNo} 打旧应用 {@code requestTicketRefund}，返回的是
     * <b>全局异常处理器的 UUID {@code retCode} + {@code retMsg=null} + 多一个 {@code data} 键</b>
     * ——取 9999 之前先抛异常了。本类返 {@code 9999} 是**修复**，
     * <b>NEVER 为了「与旧应用运行行为一致」把它改回 UUID</b>。
     * 连带判据：**「旧实现回什么码」MUST 有一次真实应答做证据，NEVER 只凭旧代码里的字面量**
     * （同 ADR-D92 那条外部网关的教训，这次踩在我方旧应用上）。</p>
     */
    public static JSONObject orderNotFound() {
        return body(CODE_ORDER_NO_ERROR, "订单号错误,没有找到匹配的订单");
    }

    /**
     * BOM 单程票退款成功响应，{@code retCode=0000} + 3 个业务键（2026-09-16 新增）。
     *
     * <p>键名与键序与 TVM 侧 {@code TvmResponses.refundSuccess} 完全一致
     * （{@code refundResult / refundResultDesc / refundNo}）—— <b>不是巧合</b>：旧
     * {@code collect-pay-server} 的 BOM 退款实现（{@code BomOrderServiceImpl.requestTicketTRefund}）
     * import 的就是 TVM 那个 {@code model.response.tvm.RequestRefundRespDTO}，两侧共用同一份 DTO。</p>
     *
     * <p><b>旧实现的运行行为只有 2 键</b>：{@code requestTicketTRefund} 成功侧回
     * {@code BomOrderResult.success()}、失败侧回 {@code fail()} / {@code failMessage()}，
     * 那份 DTO 的 3 键 {@code success(...)} 是死代码（零调用方）。本方法按 DTO 公布的结构补齐，
     * 理由同 IF2A-08：设备端按固定结构体解析，缺字段解析失败。
     * <b>NEVER 因为「旧实现只回 2 键」把这 3 个键删回去。</b></p>
     *
     * <p>注意与 TVM 侧的<b>错误码族仍然不同</b>：本类是 BOM 族（{@code 0000} / {@code 8999} /
     * {@code 8003} / {@code 8006}），加字段不改码，**NEVER 借这次改动把两族码统一**。</p>
     */
    public static JSONObject refundSuccess(String refundResult, String refundResultDesc, String refundNo) {
        return refundBody(body(CODE_SUCCESS, "成功"), refundResult, refundResultDesc, refundNo);
    }

    /**
     * BOM 单程票退款失败，<b>带与 {@link #refundSuccess} 相同的 3 个业务键、值为 JSON null</b>。
     *
     * <p>{@code retCode} 由调用方给定、逐个失败分支保持原码：参数与状态类走 {@code 8999}
     * （{@code failMessage} 的码），<b>「订单不存在」那一支仍是 {@code 9999}</b> ——
     * 见 {@link #orderNotFound()} 注释里那条「BOM 域仅此一处」，NEVER 归一成 8006 或 8999。</p>
     *
     * <p>业务值一律 JSON null，<b>NEVER 把 {@code refundResult} 填成 {@code FAILED}</b>：
     * 这些分支表达的是「请求没被受理」，不是「退款做了但失败了」。</p>
     */
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

    /**
     * IF5A-01 票卡分析结果，14 个业务 key 逐字照搬旧 {@code RequestCardDataAnalyseRespDTO}。
     *
     * <p><b>{@code lastTransAmout} 与 {@code lastTikcetTransSeq} 两个拼写错误是既有契约</b>
     * （少一个 n、Ticket 写成 Tikcet），BOM 侧已按此解析，NEVER 更正。</p>
     *
     * <p>{@code adviceOpt} 在下游是 {@code List<String>}，原样放入，由 Fastjson 序列化成数组。</p>
     */
    public static JSONObject cardDataAnalyse(String providerId, String cardIssueDate, String msisdn,
                                             String cardId, String cardStatus, String lastLineCode,
                                             String lastStationCode, String lastUpdateDate,
                                             String lastTransAmount, String lastTicketTransSeq,
                                             Object adviceOpt, String managerCode, String transAmount) {
        return cardDataAnalyseBody(body(CODE_SUCCESS, "成功"), providerId, cardIssueDate, msisdn, cardId,
                cardStatus, lastLineCode, lastStationCode, lastUpdateDate, lastTransAmount,
                lastTicketTransSeq, adviceOpt, managerCode, transAmount);
    }

    /**
     * IF5A-01 票卡分析失败，<b>带与 {@link #cardDataAnalyse} 相同的 13 个业务键、值为 JSON null</b>。
     *
     * <p><b>{@code adviceOpt} 给的是 JSON null，不是空数组</b>：成功侧那个键在下游是
     * {@code List<String>}、序列化成数组，失败侧若擅自给 {@code []} 等于告诉 BOM「分析成功、没有建议操作」，
     * 与「分析失败」不是一回事。NEVER 改成空数组或空串。</p>
     */
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

    /**
     * 单程票交易查询结果，8 个 key 照搬旧实现。
     *
     * <p>{@code paymentResult} 这里的值域是 {@code SUCCESS / FAILED / UNPAID}
     * （来自支付中心状态枚举），<b>与 {@link #paymentResult} 的
     * {@code SUCCESS / FAILED / ORDERED} 不同</b>——同一 controller 两套值域是既有契约。</p>
     *
     * <p><b>{@code transAmount} 必须序列化成字符串</b>：旧实现是
     * {@code result.put("transAmount", orderResult.getString("ticketPrice"))}
     * （{@code BomOrderServiceImpl:1314,1328}），BOM 侧一直收到的是 {@code "200"} 而不是
     * {@code 200}。这里入参保持 {@code Long}（内部都是分），只在出参处转字符串，
     * NEVER 直接 put 数值——2026-09-11 双跑对比实测到该类型漂移。</p>
     */
    public static JSONObject orderResult(String paymentResult, String paymentResultDesc, String orderNo,
                                         String transDate, Long transAmount, String paymentChannelCode) {
        return orderResultBody(body(CODE_SUCCESS, "成功"), paymentResult, paymentResultDesc, orderNo,
                transDate, transAmount == null ? null : String.valueOf(transAmount), paymentChannelCode);
    }

    /**
     * 单程票交易查询失败，<b>带与 {@link #orderResult} 相同的 6 个业务键、值为 JSON null</b>。
     *
     * <p>{@code transAmount} 同样是 null（不是字符串 {@code "0"}）——给 0 会被 BOM 读成
     * 「查到了、金额为零」。</p>
     */
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
