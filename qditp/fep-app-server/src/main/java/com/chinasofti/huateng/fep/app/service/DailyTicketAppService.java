package com.chinasofti.huateng.fep.app.service;

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
import com.chinasofti.huateng.model.app.dailyticket.TravelTicketOrderReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.TravelTicketOrderResult;


public interface DailyTicketAppService {

    DailyTicketOrderResult requestCountingOrder(DailyTicketOrderReqDTO request);


    TravelTicketOrderResult requestTravelOrder(TravelTicketOrderReqDTO request);


    DailyTicketFreeOrderResult requestOrderFree(DailyTicketFreeOrderReqDTO request);


    DailyTicketBaseResult syncOrder(DailyTicketSyncOrderReqDTO request);


    DailyTicketPayResult requestPay(DailyTicketPayReqDTO request);


    DailyTicketPayQueryResult requestPayResult(DailyTicketOrderNoReqDTO request);


    DailyTicketRefundResult requestRefundTicket(DailyTicketOrderNoReqDTO request);


    DailyTicketBaseResult cancelOrder(DailyTicketOrderNoReqDTO request);


    DailyTicketBaseResult updateTicket(DailyTicketActivateReqDTO request);


    DailyTicketBaseResult updateAndNotice(DailyTicketUsedNoticeReqDTO request);


    DailyTicketBaseResult receivePayResult(DailyTicketPayCallbackReqDTO request);


    DailyTicketBaseResult receiveRefundResult(DailyTicketRefundCallbackReqDTO request);


    DailyTicketBaseResult handlePayResultCallback(String requestBody);
}
