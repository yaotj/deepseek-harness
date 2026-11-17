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

public interface DailyTicketService {
    DailyTicketOrderResult requestCountingOrder(DailyTicketOrderReqDTO request);

    TravelTicketOrderResult requestTravelOrder(TravelTicketOrderReqDTO request);

    DailyTicketFreeOrderResult requestOrderFree(DailyTicketFreeOrderReqDTO request);

    DailyTicketBaseResult syncOrder(DailyTicketSyncOrderReqDTO request);

    DailyTicketPayResult requestPay(DailyTicketPayReqDTO request);

    DailyTicketPayQueryResult requestPayResult(DailyTicketOrderNoReqDTO request);

    DailyTicketPayQueryResult queryPayTicket(DailyTicketOrderNoReqDTO request);

    DailyTicketRefundResult requestRefundTicket(DailyTicketOrderNoReqDTO request);

    DailyTicketRefundResult queryRefundTicket(DailyTicketOrderNoReqDTO request);

    DailyTicketRefundResult retryRefundTicket(DailyTicketOrderNoReqDTO request);

    DailyTicketRefundResult resubmitRefundTicket(DailyTicketOrderNoReqDTO request);

    DailyTicketRefundResult requestTravelSubRefund(TravelTicketSubRefundRequest request);

    DailyTicketBaseResult cancelOrder(DailyTicketOrderNoReqDTO request);

    DailyTicketBaseResult updateTicket(DailyTicketActivateReqDTO request);

    DailyTicketBaseResult updateAndNotice(DailyTicketUsedNoticeReqDTO request);

    DailyTicketBaseResult receivePayResult(DailyTicketPayCallbackReqDTO request);

    DailyTicketBaseResult receiveRefundResult(DailyTicketRefundCallbackReqDTO request);

    QueryDailyTicketInfoResult queryDailyTicketInfo(QueryDailyTicketInfoReqDTO request);

    QueryDailyTicketPayInfoResult queryDailyTicketPayInfo(QueryDailyTicketPayInfoReqDTO request);


    DailyTicketBaseResult validateEntryCheck(String cardNum);


    DailyTicketBaseResult checkRideAvailability(String cardNum);


    DailyTicketBaseResult markUsed(String cardNum, Long countingEnd);


    DailyTicketBaseResult markUsed(String cardNum, Long countingEnd,
                                   String orderNo, String inStation, String outStation);


    DailyTicketBaseResult queryUsageLog(String cardNum);
}
