package com.chinasofti.huateng.dailyticket.service;

import com.chinasofti.huateng.model.app.dailyticket.DailyTicketActivateReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketBaseResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketFreeOrderReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketFreeOrderResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketOrderNoReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketOrderReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketOrderResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketPayCallbackReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketPayQueryResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketPayReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketPayResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketRefundCallbackReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketRefundResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketSyncOrderReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketUsedNoticeReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.QueryDailyTicketInfoReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.QueryDailyTicketInfoResult;
import com.chinasofti.huateng.model.app.dailyticket.QueryDailyTicketPayInfoReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.QueryDailyTicketPayInfoResult;
import com.chinasofti.huateng.model.app.dailyticket.TravelTicketOrderReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.TravelTicketOrderResult;
import com.chinasofti.huateng.dailyticket.page.TravelTicketSubRefundRequest;

/** 日票业务服务。 */
public interface DailyTicketService {
    /** 日票下单，生成日票订单号。 */
    DailyTicketOrderResult requestCountingOrder(DailyTicketOrderReqDTO request);

    /** IF8A-70 旅游票下单。生成聚合主单及其内含的日票子单。 */
    TravelTicketOrderResult requestTravelOrder(TravelTicketOrderReqDTO request);

    /** IF8A-73 免费票请求下单。 */
    DailyTicketFreeOrderResult requestOrderFree(DailyTicketFreeOrderReqDTO request);

    /** IF8A-72 小程序票状态同步。 */
    DailyTicketBaseResult syncOrder(DailyTicketSyncOrderReqDTO request);

    /** 日票支付，转换支付场景并调用支付服务。 */
    DailyTicketPayResult requestPay(DailyTicketPayReqDTO request);

    /** 查询日票支付结果。 */
    DailyTicketPayQueryResult requestPayResult(DailyTicketOrderNoReqDTO request);

    /** 运营页面主动查询支付平台结果并同步本地订单状态。 */
    DailyTicketPayQueryResult queryPayTicket(DailyTicketOrderNoReqDTO request);

    /** 日票退款。未激活可直接退款，已激活未使用后续应进入核验退款流程。 */
    DailyTicketRefundResult requestRefundTicket(DailyTicketOrderNoReqDTO request);

    /** 查询已提交到支付平台的日票退款结果。 */
    DailyTicketRefundResult queryRefundTicket(DailyTicketOrderNoReqDTO request);

    /** 使用原退款单号重新提交支付平台退款请求。 */
    DailyTicketRefundResult retryRefundTicket(DailyTicketOrderNoReqDTO request);

    /** 重提交：支付平台从未受理过的退款单（{@code PLATFORM_REFUND_NO IS NULL}）重新发起退款。 */
    DailyTicketRefundResult resubmitRefundTicket(DailyTicketOrderNoReqDTO request);

    /** 运营端按旅游票主单+子单发起部分退款。 */
    DailyTicketRefundResult requestTravelSubRefund(TravelTicketSubRefundRequest request);

    /** 取消未支付日票订单。 */
    DailyTicketBaseResult cancelOrder(DailyTicketOrderNoReqDTO request);

    /** 激活日票并生成票实例。 */
    DailyTicketBaseResult updateTicket(DailyTicketActivateReqDTO request);

    /** 标记日票已使用并通知ACC。 */
    DailyTicketBaseResult updateAndNotice(DailyTicketUsedNoticeReqDTO request);

    /** 处理支付结果回调。 */
    DailyTicketBaseResult receivePayResult(DailyTicketPayCallbackReqDTO request);

    /** 处理支付中心退款结果回调（网关文档 §5.2）。 */
    DailyTicketBaseResult receiveRefundResult(DailyTicketRefundCallbackReqDTO request);

    /** 查询日票票实例信息（ticketCode、actualTimes）。 */
    QueryDailyTicketInfoResult queryDailyTicketInfo(QueryDailyTicketInfoReqDTO request);

    /** 按日票票号查购票支付信息（{@code payTradeOrderNo} / {@code payOrderNoDate} / {@code payChannelCode}）。 */
    QueryDailyTicketPayInfoResult queryDailyTicketPayInfo(QueryDailyTicketPayInfoReqDTO request);

    /**
     * 日票进站校验（闸机入口调用）。
     *
     * @param cardNum 卡号（对应 DAILY_TICKET_INSTANCE.CARD_NUM）
     * @return retCode=0000 通过；否则 retMsg 携带拒绝原因
     */
    DailyTicketBaseResult validateEntryCheck(String cardNum);

    /**
     * 日票乘车可用性查询（IF8A-03 拉码前置的只读判定）。
     *
     * <p>四条口径：
     * <ol>
     *   <li>只读：只判定、不推进任何状态，不扣次、不改票状态；</li>
     *   <li>不能替代 {@link #validateEntryCheck}——那条是进站那一刻的权威校验、由闸机调用；
     *       拉码到进站之间可能隔很久，APP 还可能缓存旧码，因此闸机那道校验 NEVER 撤；</li>
     *   <li>调用方 {@code fep-app-server} 只在日票族卡种才调，且 daily-ticket 不可达时降级放行；</li>
     *   <li>因此本方法只用 retCode 表达结论、绝不抛异常。</li>
     * </ol>
     *
     * @param cardNum 卡号（对应 DAILY_TICKET_INSTANCE.CARD_NUM）
     * @return retCode=0000 可用；否则不可用，retMsg 携带原因
     */
    DailyTicketBaseResult checkRideAvailability(String cardNum);

    /**
     * 日票出站处理（闸机出站时调用）。
     *
     * @param cardNum 卡号
     * @param countingEnd 有效期截止时间（毫秒时间戳）
     * @return 处理结果
     */
    DailyTicketBaseResult markUsed(String cardNum, Long countingEnd);

    /**
     * 日票出站处理（扩展版，携带行程关联信息用于扣次明细记录）。
     *
     * @param cardNum 卡号
     * @param countingEnd 有效期截止时间（毫秒时间戳）
     * @param orderNo 关联 GATE_TXN_PAY.ORDER_NO
     * @param inStation 进站编码
     * @param outStation 出站编码
     * @return 处理结果
     */
    DailyTicketBaseResult markUsed(String cardNum, Long countingEnd,
                                   String orderNo, String inStation, String outStation);

    /**
     * 查询某张日票的扣次使用明细（按创建时间倒序）。
     *
     * @param cardNum 日票虚拟卡号
     * @return 扣次明细列表（retCode=0000 时 data 有值）
     */
    DailyTicketBaseResult queryUsageLog(String cardNum);
}
