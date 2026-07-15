package com.chinasofti.huateng.dailyticket.controller;

import com.chinasofti.huateng.dailyticket.service.DailyTicketService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 日票服务内部业务接口。
 *
 * <p>供fep-app-server通过RPC调用，负责日票下单、支付、查询、退款、取消、激活和使用通知。</p>
 */
@RestController
@RequestMapping("/ci/daily-ticket")
public class DailyTicketController {
    private final DailyTicketService dailyTicketService;

    public DailyTicketController(DailyTicketService dailyTicketService) {
        this.dailyTicketService = dailyTicketService;
    }

    /**
     * IF8A-60 日票下单。
     */
    @PostMapping("/requestCountingOrder")
    public DailyTicketOrderResult requestCountingOrder(@RequestBody DailyTicketOrderReqDTO request) {
        return dailyTicketService.requestCountingOrder(request);
    }

    /**
     * IF8A-61 日票支付。
     */
    @PostMapping("/payment/requestPay")
    public DailyTicketPayResult requestPay(@RequestBody DailyTicketPayReqDTO request) {
        return dailyTicketService.requestPay(request);
    }

    /**
     * IF8A-62 日票支付结果查询。
     */
    @PostMapping("/payment/requestPayResult")
    public DailyTicketPayQueryResult requestPayResult(@RequestBody DailyTicketOrderNoReqDTO request) {
        return dailyTicketService.requestPayResult(request);
    }

    /**
     * IF8A-64 日票退款。
     */
    @PostMapping("/payment/requestRefundTicket")
    public DailyTicketRefundResult requestRefundTicket(@RequestBody DailyTicketOrderNoReqDTO request) {
        return dailyTicketService.requestRefundTicket(request);
    }

    /**
     * IF8A-65 日票取消订单。
     */
    @PostMapping("/ticket/cancelOrder")
    public DailyTicketBaseResult cancelOrder(@RequestBody DailyTicketOrderNoReqDTO request) {
        return dailyTicketService.cancelOrder(request);
    }

    /**
     * IF8A-67 日票激活。
     */
    @PostMapping("/ticket/updateTicket")
    public DailyTicketBaseResult updateTicket(@RequestBody DailyTicketActivateReqDTO request) {
        return dailyTicketService.updateTicket(request);
    }

    /**
     * IF8A-71 通知ACC车票已使用。
     */
    @PostMapping("/ticket/updateAndNotice")
    public DailyTicketBaseResult updateAndNotice(@RequestBody DailyTicketUsedNoticeReqDTO request) {
        return dailyTicketService.updateAndNotice(request);
    }

    /**
     * 支付结果回调内部入口。
     */
    @PostMapping("/payment/receivePayResult")
    public DailyTicketBaseResult receivePayResult(@RequestBody DailyTicketPayCallbackReqDTO request) {
        return dailyTicketService.receivePayResult(request);
    }
}
