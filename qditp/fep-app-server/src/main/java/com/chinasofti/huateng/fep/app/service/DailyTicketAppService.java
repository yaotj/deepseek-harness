package com.chinasofti.huateng.fep.app.service;

import com.chinasofti.huateng.model.app.dailyticket.DailyTicketActivateReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketBaseResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketOrderNoReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketOrderReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketOrderResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketPayQueryResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketPayReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketPayResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketRefundResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketUsedNoticeReqDTO;

/**
 * APP日票业务前置服务。
 */
public interface DailyTicketAppService {
    /**
     * IF8A-60 日票下单。
     */
    DailyTicketOrderResult requestCountingOrder(DailyTicketOrderReqDTO request);

    /**
     * IF8A-61 日票支付。
     */
    DailyTicketPayResult requestPay(DailyTicketPayReqDTO request);

    /**
     * IF8A-62 日票支付结果查询。
     */
    DailyTicketPayQueryResult requestPayResult(DailyTicketOrderNoReqDTO request);

    /**
     * IF8A-64 日票退款。
     */
    DailyTicketRefundResult requestRefundTicket(DailyTicketOrderNoReqDTO request);

    /**
     * IF8A-65 日票取消订单。
     */
    DailyTicketBaseResult cancelOrder(DailyTicketOrderNoReqDTO request);

    /**
     * IF8A-67 日票激活。
     */
    DailyTicketBaseResult updateTicket(DailyTicketActivateReqDTO request);

    /**
     * IF8A-71 通知ACC车票已使用。
     */
    DailyTicketBaseResult updateAndNotice(DailyTicketUsedNoticeReqDTO request);
}
