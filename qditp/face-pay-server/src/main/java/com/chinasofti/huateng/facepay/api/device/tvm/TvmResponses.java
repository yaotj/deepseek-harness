package com.chinasofti.huateng.facepay.api.device.tvm;

import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.facepay.api.device.DeviceRetCode;
import com.chinasofti.huateng.facepay.api.device.PaymentResult;
import com.chinasofti.huateng.facepay.support.F2fChannel;

/**
 * TVM 响应报文组装。<b>响应体的每个 key 都是既有契约</b>，逐字照搬旧 {@code TvmOrderResult} +
 * {@code DeviceResponse}，NEVER 改名、NEVER 增删。
 *
 * <p>形态是「外层 {@code retCode} / {@code retMsg} + 业务字段平铺在同一层」，不是嵌套 data。
 * 旧实现用 {@code successData(JSONObject)} 把 {@code retCode} 直接 put 进业务体里，本类保持一致。</p>
 *
 * <p>返回 Fastjson2 的 {@link JSONObject}（本身就是 {@code Map}），与旧实现的 Fastjson 1.x
 * {@code JSONObject} 在 MVC 序列化后形态一致。</p>
 *
 * <p><b>2026-09-16 起，凡是「成功响应带业务字段」的接口，其失败分支 MUST 也回同一套键、值全为
 * JSON null</b>（用户明确要求：「参考 requestTakeTicketAuth，错误也要有全量字段，保证设备端能正常编译」）。
 * 设备侧按固定结构体解析，键缺失会导致解析/编译失败 —— 只回 {@code retCode/retMsg} 的裸错误体
 * 在那一侧是不可解析的报文。落地形态是每类响应各抽一个私有 {@code *Body(...)} 组装方法，
 * 成功与失败两支共用，<b>NEVER 在失败方法里再抄一份 put 列表</b>（否则以后改成功分支的键会漏掉失败支）。</p>
 *
 * <p><b>{@link #fail(DeviceRetCode, String)} / {@link #fail(DeviceRetCode)} 这两个只有两键的
 * 通用失败构造 NEVER 删、NEVER 加业务字段</b>：它们服务于成功侧本来就只有 {@code retCode/retMsg}
 * 的接口（设备心跳、出票上报、充值结果通知、激活取票），那些接口加字段反而是契约变更。
 * <b>设备退款（IF2A {@code requestRefund}）已于 2026-09-16 移出这份名单</b> ——
 * 它现在回 3 个业务键，见 {@link #refundSuccess}，本行此前把它列在这里已作废、NEVER 回退。</p>
 */
public final class TvmResponses {

    private TvmResponses() {
    }

    /** 拉码下单成功：{@code retCode/retMsg + orderNo + payUrl}。{@code payUrl} 整串由设备显示成二维码，设备不解析。 */
    public static JSONObject genSjtOrderSuccess(String orderNo, String payUrl) {
        return withRetCode(genSjtOrderBody(orderNo, payUrl), DeviceRetCode.SUCCESS);
    }

    /**
     * IF2A-01 拉码下单失败，<b>带与成功响应相同的 {@code orderNo} / {@code payUrl} 两键、值为 JSON null</b>。
     *
     * <p>{@code retCode} 由调用方给定，逐个失败分支保持原码（参数校验 {@code 2002}、
     * 支付中心被拒或状态不明 {@code 2999}），<b>NEVER 借这次改动统一错误码</b>。</p>
     *
     * <p>同一条 URL 上 {@code providerId=03} 走 BOM 售票、成功体只有 {@code orderNo}
     * （{@code BomResponses.successOrderNo}），键集是本方法的子集。校验发生在 controller 分流<b>之前</b>，
     * 拿不到 provider，故统一回本方法 —— 多出来的 {@code payUrl:null} 对 BOM 是冗余键、不影响结构体取值，
     * 而少键才会让设备解析失败。</p>
     */
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
     * 查询支付结果。<b>外层 {@code retCode} 随 {@code paymentResult} 变，NEVER 写死 0000</b>。
     *
     * <p>旧实现分两个包装：成功走 {@code TvmOrderResult.successData} 给 {@code 0000/成功}，
     * 支付失败与未支付走 {@code failData} 给 <b>{@code 2999/失败}</b>
     * （`TvmOrderServiceImpl:302~310`、`TvmPayCodeEnum.FAIL`）。2026-09-13 用两笔真实历史失败单
     * （`00202609111620275127` 拉码、`08202609111700165143` 充值）重放实测：旧回 {@code 2999/失败}、
     * 新回 {@code 0000/成功}，内层 {@code paymentResult:FAILED} 两边一致。
     * <b>TVM 设备若按外层码判成败，这条差异会把失败单读成成功</b>，据此改为按结果取码。</p>
     *
     * <p>{@code ORDERED}（支付中）仍是 {@code 0000} —— 2026-09-11 的 34 例 A/B 里
     * {@code TVM_payResult_unpaid} 两侧本就一致，**NEVER 顺手把它也改成 2999**。</p>
     *
     * @param channel 支付渠道码，原样回吐（可能为 null，旧实现不做兜底）
     */
    public static JSONObject payResult(PaymentResult result, String channel) {
        JSONObject body = payResultBody(result.getCode(), result.getMsg(), channel);
        return withRetCode(body, result == PaymentResult.FAILED
                ? DeviceRetCode.FAIL : DeviceRetCode.SUCCESS);
    }

    /**
     * IF2A-03 查询支付结果失败（参数缺失 / 订单不存在），
     * <b>带与成功响应相同的 3 个业务键、值为 JSON null</b>。
     *
     * <p><b>业务值 MUST 全 null，NEVER 顺手填成 {@code FAILED}</b>：这一支的语义是「这笔查不到」，
     * 不是「这笔支付失败了」。设备若把它读成支付失败，会对一笔可能已扣款的单子做错误处置。
     * 码仍保持 {@code 2002}（参数校验族），与查得到但失败时的 {@code 2999} 区分开。</p>
     */
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

    /** 充值下单成功。与拉码下单同形态（{@code orderNo + payUrl}），旧实现是两个方法但字段一致。 */
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

    /**
     * 取票鉴权成功，8 个业务字段 + retCode/retMsg。
     *
     * <p><b>{@code singelTicketNum} 的拼写错误是既有契约</b>（旧 {@code DeviceResponse} 即如此）。
     * 旧实现用 {@code String.valueOf(getTicketNum())} 在票数为 null 时产出字面量 {@code "null"}，
     * 设备侧可能误解析成 1；本实现票数为 null 时直接给 JSON null。</p>
     */
    public static JSONObject takeTicketAuthSuccess(String orderNo, String deviceId,
                                                  String entryStationCode, String exitStationCode,
                                                  Long ticketPrice, Integer ticketNum,
                                                  String singleTicketType, String paymentChannelCode) {
        JSONObject body = takeTicketAuthBody(orderNo, deviceId, entryStationCode, exitStationCode,
                ticketPrice, ticketNum, singleTicketType, paymentChannelCode);
        return withRetCode(body, DeviceRetCode.SUCCESS);
    }

    /**
     * 取票鉴权「无激活的订单」，{@code retCode=2003} + <b>与成功响应完全相同的 8 个业务键、值全为 JSON null</b>。
     *
     * <p>按用户 2026-09-16 的明确要求：查不到取票订单时不再回「只有 retCode/retMsg 的纯错误体」，
     * 而是回与成功响应同形的结构体、业务值全空；<b>码与文案仍是 {@code 2003 无激活的订单}，NEVER 改</b>。</p>
     *
     * <p><b>值 MUST 是 JSON null，NEVER 是字符串 {@code "null"} 或空串</b>——旧 collect-pay 实现在
     * 「查到行但未激活」那支回的是 {@code failData(空 TvmAppOrder)}，其 {@code String.valueOf(null)}
     * 产出字面量 {@code "null"}，设备侧可能把 {@code singelTicketNum} 误解析成 1 张票而多出一张票。
     * 本方法只恢复「键齐全」，不恢复那个字面量缺陷；同时也不恢复旧的 {@code 2999} 码。</p>
     *
     * <p>键名与键序 MUST 与 {@link #takeTicketAuthSuccess} 一致，故两者共用
     * {@link #takeTicketAuthBody}——**NEVER 在本方法里再抄一份 put 列表**，否则以后改成功分支的键
     * 会漏掉这一支。</p>
     */
    public static JSONObject takeTicketAuthNoActiveOrder() {
        return takeTicketAuthFail(DeviceRetCode.NO_ACTIVE_ORDER, DeviceRetCode.NO_ACTIVE_ORDER.getMsg());
    }

    /**
     * 取票鉴权的通用失败应答，键集同 {@link #takeTicketAuthSuccess}、业务值全为 JSON null。
     *
     * <p>{@link #takeTicketAuthNoActiveOrder()} 是它的 {@code 2003} 特例；controller 的入参校验
     * 分支用 {@code 2002} 走本方法。<b>码由调用方给定，NEVER 在本方法里写死</b>。</p>
     */
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
     * <p>两处照搬的怪异之处，NEVER「修正」：</p>
     * <ul>
     *   <li>站名与站码取同一个值（旧实现留着 {@code // todo 需改为中文名}）；</li>
     *   <li>{@code orderStatus} 在「支付失败 / 未支付」时是<b>空字符串</b>而不是某个码。</li>
     * </ul>
     *
     * <p><b>本接口的失败分支刻意没有做「全量 null 键」改造</b>（与本类其它带业务字段的响应不同）：
     * 调用方是<b>支付中心收银台，不是设备</b>，不存在「按固定结构体解析导致编译失败」的问题；
     * 且购票单 13 键与充值单 12 键的<b>键集本就不同</b>（见 {@link #topupOrderDetail}），
     * 订单查不到时无法判断该回哪一套。要改 MUST 先与支付中心确认按哪套键，
     * <b>NEVER 自行挑一套补上</b> —— 挑错等于给收银台一份它不认识的报文。</p>
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
        // MUST 输出字符串。旧实现 TvmPayOrder.totalPrice 是 String 字段，响应里是 "totalTicketPrice":"200"；
        // 新表 ORDER_AMOUNT 是 NUMBER，直接 put 会序列化成 200（数字）。对端是支付中心收银台，
        // 按 String 取值时数字会解析失败。2026-09-10 双跑重放对比实测发现，NEVER 改回数字。
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
     * 充值单的订单详情，键集与购票单**不同**，逐字照搬旧
     * {@code TvmTopupServiceImpl.getPayCenterPayOrderDetailResult}：
     *
     * <ul>
     *   <li>没有 {@code singleGetoffStationName / Code} 两个键（充值无出站）；</li>
     *   <li>多一个 {@code singleTicketPrice}，与 {@code totalTicketPrice} 同值；</li>
     *   <li>{@code singleTicketNum} 是<b>字符串 "1"</b>，而购票单那边是数字。这个类型差异
     *       是旧实现的既有形态，收银台已按此解析，NEVER 统一成数字；</li>
     *   <li>上车站码取 {@code deviceId} 前 4 位（旧实现同样留着「需改为中文名」的 todo）。</li>
     * </ul>
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
     * <p><b>2026-09-16 补齐的业务键</b>：`retCode/retMsg` 之外还回
     * {@code refundResult / refundResultDesc / refundNo}，键名与键序逐字照搬旧
     * {@code collect-pay-server} 的 {@code RequestRefundRespDTO}（那个类的 5 个字段就是
     * 对设备公布的响应结构，设备侧按它生成结构体）。</p>
     *
     * <p><b>注意旧实现自身是不自洽的</b>：`RequestRefundRespDTO.success(...)` 定义了这 3 个键，
     * 但 {@code TvmOrderServiceImpl.requestRefund:752} 实际回的是 {@code TvmOrderResult.success()}
     * ——只有 2 键，那个 3 键的 success 方法**零调用方、是死代码**。因此本方法**不是恢复旧行为、
     * 而是按 DTO 公布的结构补齐**（用户 2026-09-16 明确要求「也添加业务字段」，
     * 理由同 IF2A-08：设备端按固定结构体解析，缺字段编译/解析失败）。
     * <b>NEVER 因为「旧实现只回 2 键」把这 3 个键删回去。</b></p>
     *
     * @param refundResult     {@code PROCESSING} / {@code SUCCESS} / {@code FAILED}
     * @param refundResultDesc 结果描述
     * @param refundNo         我方退款单号
     */
    public static JSONObject refundSuccess(String refundResult, String refundResultDesc, String refundNo) {
        return withRetCode(refundBody(refundResult, refundResultDesc, refundNo), DeviceRetCode.SUCCESS);
    }

    /**
     * 退款接口专用失败响应，{@code retCode=9999}。
     *
     * <p>旧 {@code requestRefund} 用的是 {@code RequestRefundRespDTO.fail("9999", ...)}，
     * 与 TVM 的 2xxx、BOM 的 8999 都不同——同一个服务里三套错误码族并存。
     * 这是既有契约，设备侧已按此解析，NEVER 统一成 2999。</p>
     *
     * <p>业务键与 {@link #refundSuccess} 同一套、值全 JSON null。
     * <b>NEVER 把 {@code refundResult} 填成 {@code FAILED}</b>：参数非法 / 订单不存在 /
     * 状态不可退这些分支表达的是「请求没被受理」，不是「退款做了但失败了」，
     * 后者只能由 {@code F2F_REFUND.REFUND_STATUS} 收口后经查询或通知给出。</p>
     */
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

    /**
     * 设备退款「订单不存在」应答，{@code retCode=2002}、文案同 {@link #ORDER_NOT_FOUND_MSG}。
     *
     * <p><b>与同一接口其它失败分支的 9999 不同码</b>：旧 {@code requestRefund} 在查不到单时
     * 走的是参数校验族的 {@code 2002}，只有业务失败才用 {@code 9999}。
     * 2026-09-11 新旧双打实测：同一报文旧返 {@code 2002}、新返 {@code 9999}，retMsg 一致，据此改回。
     * NEVER 把本方法并回 {@link #refundFail(String)}。</p>
     *
     * <p>业务键与 {@link #refundSuccess} 同一套、值全 JSON null（2026-09-16 补齐）。</p>
     */
    public static JSONObject refundOrderNotFound() {
        return withRetCode(refundBody(null, null, null),
                DeviceRetCode.INVALID_PARAM, ORDER_NOT_FOUND_MSG);
    }

    /**
     * 出票<b>成功</b>上报的「订单不存在」应答，<b>按 {@code providerId} 分两个码</b>：
     * {@code providerId=03} 回 {@code 2999}，其余（含缺失）回 {@code -1}。
     *
     * <p>旧 {@code TvmOrderServiceImpl.notiTakeTicketResult:371} 写的是 {@code fail("-1", ...)}，
     * 而紧邻的 {@code notiTakeTicketFailResult:579} 写的是 {@code failMessage(...)} 落到 2999——
     * <b>同一对上报接口的两个码不一样</b>。这看着像旧代码的随手写法，但 TVM 已按此解析，
     * NEVER 把两者统一，也 NEVER 改成 {@code DeviceRetCode.ORDER_NO_ERROR}（2006）。</p>
     *
     * <p><b>{@code providerId} 分叉是 2026-09-16 新旧双打实测补回的（ADR-D112）</b>：旧
     * {@code TvmOrderController:163-176} 按 {@code providerId=="03"} 把这条 URL 分流到
     * {@code BomOrderServiceImpl:1137}，那边走 {@code failMessage(...)} 落 {@code 2999}。
     * 同一份报文分别打 30024 / 30025 的实测矩阵（订单号取不存在的
     * {@code 00209999999999999999}）：</p>
     * <ul>
     *   <li>旧 {@code providerId=03} → {@code 2999}；旧 {@code 02} / {@code 01} → {@code -1}</li>
     *   <li>新（修复前）三者一律 {@code -1} ⇒ <b>BOM 设备那一支是契约回归</b>，
     *       而现场 TVM / BOM 设备发的正是 {@code providerId=03}</li>
     *   <li>兄弟接口 {@code notiTakeTicketFailResult} 两侧三种 {@code providerId} 全是
     *       {@code 2999}，本来就一致、不需要分叉</li>
     * </ul>
     * <p>判据 <b>MUST 用 {@code providerId} 本身，NEVER 换成 {@code channelOf(...)} 算出来的
     * 渠道码</b>：{@code BomOrderController.channelOf} 在 {@code providerId} 缺失时兜底 BOM
     * （ADR-D97 那两条别名），用渠道码判会把「BOM 前缀 + 无 providerId」也判成 2999，
     * 而旧实现那一支根本不存在、没有基线。</p>
     */
    public static JSONObject takeTicketResultOrderNotFound(String providerId) {
        JSONObject body = new JSONObject();
        body.put("retCode", F2fChannel.BOM.equals(providerId) ? "2999" : "-1");
        body.put("retMsg", ORDER_NOT_FOUND_MSG);
        return body;
    }

    /**
     * 出票<b>失败</b>上报的「订单不存在」应答，{@code retCode=2999}。
     *
     * <p>2999 是旧 {@code TvmOrderResult.failMessage(String)} 的默认码（{@code TvmPayCodeEnum.FAIL}），
     * 不是 2006。见上一个方法的说明。</p>
     */
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

    /**
     * 把 {@code retCode/retMsg} 追加到已组装好的业务体末尾。
     *
     * <p><b>MUST 在业务键之后 put</b>：旧实现即「业务体在前、码在后」，本类全部构造保持同一键序。</p>
     */
    private static JSONObject withRetCode(JSONObject body, DeviceRetCode retCode, String retMsg) {
        body.put("retCode", retCode.getCode());
        body.put("retMsg", retMsg);
        return body;
    }
}
