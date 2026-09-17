package com.chinasofti.huateng.dailyticket.service;

import com.chinasofti.huateng.model.app.dailyticket.DailyTicketActivateReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketBaseResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketOrderNoReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketOrderReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketOrderResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketPayCallbackReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketPayQueryResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketPayReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketPayResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketRefundCallbackReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketRefundResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketUsedNoticeReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.QueryDailyTicketInfoReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.QueryDailyTicketInfoResult;
import com.chinasofti.huateng.model.app.dailyticket.QueryDailyTicketPayInfoReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.QueryDailyTicketPayInfoResult;
import com.chinasofti.huateng.model.app.dailyticket.TravelTicketOrderReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.TravelTicketOrderResult;
import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.dailyticket.page.DailyTicketRefundOrderQuery;
import com.chinasofti.huateng.dailyticket.page.DailyTicketRefundOrderView;
import com.chinasofti.huateng.dailyticket.page.DailyTicketRefundQuery;
import com.chinasofti.huateng.dailyticket.page.DailyTicketRefundView;
import com.github.pagehelper.PageInfo;

/** 日票业务服务。 */
public interface DailyTicketService {
    /** 日票下单，生成日票订单号。 */
    DailyTicketOrderResult requestCountingOrder(DailyTicketOrderReqDTO request);

    /** IF8A-70 旅游票下单。生成聚合主单及其内含的日票子单。 */
    TravelTicketOrderResult requestTravelOrder(TravelTicketOrderReqDTO request);

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

    /** 运营页面分页查询日票订单及退款、票实例摘要。 */
    ResultVO<PageInfo<DailyTicketRefundOrderView>> pageRefundOrders(DailyTicketRefundOrderQuery query);

    /** 运营页面分页查询日票退款记录。 */
    ResultVO<PageInfo<DailyTicketRefundView>> pageRefundRecords(DailyTicketRefundQuery query);

    /** 取消未支付日票订单。 */
    DailyTicketBaseResult cancelOrder(DailyTicketOrderNoReqDTO request);

    /** 激活日票并生成票实例。 */
    DailyTicketBaseResult updateTicket(DailyTicketActivateReqDTO request);

    /** 标记日票已使用并通知ACC。 */
    DailyTicketBaseResult updateAndNotice(DailyTicketUsedNoticeReqDTO request);

    /** 处理支付结果回调。 */
    DailyTicketBaseResult receivePayResult(DailyTicketPayCallbackReqDTO request);

    /** 处理支付中心退款结果回调（网关文档 §3.3）。 */
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
