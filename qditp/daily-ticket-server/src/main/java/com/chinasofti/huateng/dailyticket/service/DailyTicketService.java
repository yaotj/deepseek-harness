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
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketRefundResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketUsedNoticeReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.QueryDailyTicketInfoReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.QueryDailyTicketInfoResult;
import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.dailyticket.page.DailyTicketRefundOrderQuery;
import com.chinasofti.huateng.dailyticket.page.DailyTicketRefundOrderView;
import com.chinasofti.huateng.dailyticket.page.DailyTicketRefundQuery;
import com.chinasofti.huateng.dailyticket.page.DailyTicketRefundView;
import com.github.pagehelper.PageInfo;

/**
 * 日票业务服务。
 */
public interface DailyTicketService {
    /**
     * 日票下单，生成日票订单号。
     */
    DailyTicketOrderResult requestCountingOrder(DailyTicketOrderReqDTO request);

    /**
     * 日票支付，转换支付场景并调用支付服务。
     */
    DailyTicketPayResult requestPay(DailyTicketPayReqDTO request);

    /**
     * 查询日票支付结果。
     */
    DailyTicketPayQueryResult requestPayResult(DailyTicketOrderNoReqDTO request);

    /** 运营页面主动查询支付平台结果并同步本地订单状态。 */
    DailyTicketPayQueryResult queryPayTicket(DailyTicketOrderNoReqDTO request);

    /**
     * 日票退款。未激活可直接退款，已激活未使用后续应进入核验退款流程。
     */
    DailyTicketRefundResult requestRefundTicket(DailyTicketOrderNoReqDTO request);

    /** 查询已提交到支付平台的日票退款结果。 */
    DailyTicketRefundResult queryRefundTicket(DailyTicketOrderNoReqDTO request);

    /** 使用原退款单号重新提交支付平台退款请求。 */
    DailyTicketRefundResult retryRefundTicket(DailyTicketOrderNoReqDTO request);

    /** 运营页面分页查询日票订单及退款、票实例摘要。 */
    ResultVO<PageInfo<DailyTicketRefundOrderView>> pageRefundOrders(DailyTicketRefundOrderQuery query);

    /** 运营页面分页查询日票退款记录。 */
    ResultVO<PageInfo<DailyTicketRefundView>> pageRefundRecords(DailyTicketRefundQuery query);

    /**
     * 取消未支付日票订单。
     */
    DailyTicketBaseResult cancelOrder(DailyTicketOrderNoReqDTO request);

    /**
     * 激活日票并生成票实例。
     */
    DailyTicketBaseResult updateTicket(DailyTicketActivateReqDTO request);

    /**
     * 标记日票已使用并通知ACC。
     */
    DailyTicketBaseResult updateAndNotice(DailyTicketUsedNoticeReqDTO request);

    /**
     * 处理支付结果回调。
     */
    DailyTicketBaseResult receivePayResult(DailyTicketPayCallbackReqDTO request);

    /**
     * 查询日票票实例信息（ticketCode、actualTimes）。
     */
    QueryDailyTicketInfoResult queryDailyTicketInfo(QueryDailyTicketInfoReqDTO request);

    /**
     * 日票进站校验（闸机入口调用）。
     * <p>校验规则：
     * <ul>
     *   <li>有效期校验：当前时间在 countingStart ~ countingEnd 范围内</li>
     *   <li>未出站校验：存在未完成出站的票实例</li>
     *   <li>计次票次数检查：次数为0则拒绝进站（不扣减，扣减在出站时执行）</li>
     * </ul>
     *
     * @param cardNum 卡号（对应 DAILY_TICKET_INSTANCE.CARD_NUM）
     * @return retCode=0000 通过；否则 retMsg 携带拒绝原因
     */
    DailyTicketBaseResult validateEntryCheck(String cardNum);

    /**
     * 日票出站处理（闸机出站时调用）。
     * <p>处理规则：
     * <ul>
     *   <li>计次票扣减一次可用次数（actualTimes - 1），次数≤0则保持0</li>
     *   <li>标记 TICKET_STATUS = USED，记录首次使用时间、结束时间</li>
     * </ul>
     *
     * @param cardNum 卡号
     * @param countingEnd 有效期截止时间（毫秒时间戳）
     * @return 处理结果
     */
    DailyTicketBaseResult markUsed(String cardNum, Long countingEnd);
}
