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

    /**
     * 日票退款。未激活可直接退款，已激活未使用后续应进入核验退款流程。
     */
    DailyTicketRefundResult requestRefundTicket(DailyTicketOrderNoReqDTO request);

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
}
