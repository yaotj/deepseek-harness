package com.chinasofti.huateng.fep.app.service.impl;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.fep.app.service.DailyTicketAppService;
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
import com.chinasofti.huateng.rpc.dailyticket.DailyTicketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * APP日票业务前置服务实现。
 *
 * <p>fep-app-server只承接APP入口和服务编排，日票订单、支付、激活、退款等状态由daily-ticket-server维护。</p>
 */
@Service
public class DailyTicketAppServiceImpl implements DailyTicketAppService {
    private static final Logger log = LoggerFactory.getLogger(DailyTicketAppServiceImpl.class);

    private final DailyTicketClient dailyTicketClient;

    public DailyTicketAppServiceImpl(DailyTicketClient dailyTicketClient) {
        this.dailyTicketClient = dailyTicketClient;
    }

    @Override
    public DailyTicketOrderResult requestCountingOrder(DailyTicketOrderReqDTO request) {
        log.info("call daily-ticket requestCountingOrder request={}", JSON.toJSONString(request));
        DailyTicketOrderResult result = dailyTicketClient.requestCountingOrder(request);
        log.info("call daily-ticket requestCountingOrder response={}", JSON.toJSONString(result));
        return result;
    }

    @Override
    public DailyTicketPayResult requestPay(DailyTicketPayReqDTO request) {
        log.info("call daily-ticket requestPay request={}", JSON.toJSONString(request));
        DailyTicketPayResult result = dailyTicketClient.requestPay(request);
        log.info("call daily-ticket requestPay response={}", JSON.toJSONString(result));
        return result;
    }

    @Override
    public DailyTicketPayQueryResult requestPayResult(DailyTicketOrderNoReqDTO request) {
        log.info("call daily-ticket requestPayResult request={}", JSON.toJSONString(request));
        DailyTicketPayQueryResult result = dailyTicketClient.requestPayResult(request);
        log.info("call daily-ticket requestPayResult response={}", JSON.toJSONString(result));
        return result;
    }

    @Override
    public DailyTicketRefundResult requestRefundTicket(DailyTicketOrderNoReqDTO request) {
        log.info("call daily-ticket requestRefundTicket request={}", JSON.toJSONString(request));
        DailyTicketRefundResult result = dailyTicketClient.requestRefundTicket(request);
        log.info("call daily-ticket requestRefundTicket response={}", JSON.toJSONString(result));
        return result;
    }

    @Override
    public DailyTicketBaseResult cancelOrder(DailyTicketOrderNoReqDTO request) {
        log.info("call daily-ticket cancelOrder request={}", JSON.toJSONString(request));
        DailyTicketBaseResult result = dailyTicketClient.cancelOrder(request);
        log.info("call daily-ticket cancelOrder response={}", JSON.toJSONString(result));
        return result;
    }

    @Override
    public DailyTicketBaseResult updateTicket(DailyTicketActivateReqDTO request) {
        log.info("call daily-ticket updateTicket request={}", JSON.toJSONString(request));
        DailyTicketBaseResult result = dailyTicketClient.updateTicket(request);
        log.info("call daily-ticket updateTicket response={}", JSON.toJSONString(result));
        return result;
    }

    @Override
    public DailyTicketBaseResult updateAndNotice(DailyTicketUsedNoticeReqDTO request) {
        log.info("call daily-ticket updateAndNotice request={}", JSON.toJSONString(request));
        DailyTicketBaseResult result = dailyTicketClient.updateAndNotice(request);
        log.info("call daily-ticket updateAndNotice response={}", JSON.toJSONString(result));
        return result;
    }
}
