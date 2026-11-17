package com.chinasofti.huateng.dailyticket.controller;

import com.chinasofti.huateng.dailyticket.service.DailyTicketService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ci/daily-ticket")
public class DailyTicketController {
    private final DailyTicketService dailyTicketService;

    public DailyTicketController(DailyTicketService dailyTicketService) {
        this.dailyTicketService = dailyTicketService;
    }

    @PostMapping("/requestCountingOrder")
    public DailyTicketOrderResult requestCountingOrder(@RequestBody DailyTicketOrderReqDTO request) {
        return dailyTicketService.requestCountingOrder(request);
    }

    @PostMapping("/requestTravelOrder")
    public TravelTicketOrderResult requestTravelOrder(@RequestBody TravelTicketOrderReqDTO request) {
        return dailyTicketService.requestTravelOrder(request);
    }

    @PostMapping("/requestOrderFree")
    public DailyTicketFreeOrderResult requestOrderFree(@RequestBody DailyTicketFreeOrderReqDTO request) {
        return dailyTicketService.requestOrderFree(request);
    }

    @PostMapping("/syncOrder")
    public DailyTicketBaseResult syncOrder(@RequestBody DailyTicketSyncOrderReqDTO request) {
        return dailyTicketService.syncOrder(request);
    }

    @PostMapping("/payment/requestPay")
    public DailyTicketPayResult requestPay(@RequestBody DailyTicketPayReqDTO request) {
        return dailyTicketService.requestPay(request);
    }

    @PostMapping("/payment/requestPayResult")
    public DailyTicketPayQueryResult requestPayResult(@RequestBody DailyTicketOrderNoReqDTO request) {
        return dailyTicketService.requestPayResult(request);
    }

    @PostMapping("/payment/requestRefundTicket")
    public DailyTicketRefundResult requestRefundTicket(@RequestBody DailyTicketOrderNoReqDTO request) {
        return dailyTicketService.requestRefundTicket(request);
    }

    @PostMapping("/ticket/cancelOrder")
    public DailyTicketBaseResult cancelOrder(@RequestBody DailyTicketOrderNoReqDTO request) {
        return dailyTicketService.cancelOrder(request);
    }

    @PostMapping("/ticket/updateTicket")
    public DailyTicketBaseResult updateTicket(@RequestBody DailyTicketActivateReqDTO request) {
        return dailyTicketService.updateTicket(request);
    }

    @PostMapping("/ticket/updateAndNotice")
    public DailyTicketBaseResult updateAndNotice(@RequestBody DailyTicketUsedNoticeReqDTO request) {
        return dailyTicketService.updateAndNotice(request);
    }

    @PostMapping("/payment/receivePayResult")
    public DailyTicketBaseResult receivePayResult(@RequestBody DailyTicketPayCallbackReqDTO request) {
        return dailyTicketService.receivePayResult(request);
    }

    @PostMapping("/payment/receiveRefundResult")
    public DailyTicketBaseResult receiveRefundResult(@RequestBody DailyTicketRefundCallbackReqDTO request) {
        return dailyTicketService.receiveRefundResult(request);
    }

    @PostMapping("/queryDailyTicketInfo")
    public QueryDailyTicketInfoResult queryDailyTicketInfo(@RequestBody QueryDailyTicketInfoReqDTO request) {
        return dailyTicketService.queryDailyTicketInfo(request);
    }

    @PostMapping("/queryDailyTicketPayInfo")
    public QueryDailyTicketPayInfoResult queryDailyTicketPayInfo(@RequestBody QueryDailyTicketPayInfoReqDTO request) {
        return dailyTicketService.queryDailyTicketPayInfo(request);
    }


    @PostMapping("/entry/check")
    public DailyTicketBaseResult entryCheck(@RequestBody java.util.Map<String, String> request) {
        String cardNum = request == null ? null : request.get("cardNum");
        return dailyTicketService.validateEntryCheck(cardNum);
    }


    @PostMapping("/ticket/rideAvailability")
    public DailyTicketBaseResult rideAvailability(@RequestBody java.util.Map<String, String> request) {
        String cardNum = request == null ? null : request.get("cardNum");
        return dailyTicketService.checkRideAvailability(cardNum);
    }


    @PostMapping("/ticket/markUsed")
    public DailyTicketBaseResult markUsed(@RequestBody java.util.Map<String, Object> request) {
        String cardNum = request == null ? null : (String) request.get("cardNum");
        Object rawCountingEnd = request == null ? null : request.get("countingEnd");
        Long countingEnd = null;
        if (rawCountingEnd instanceof Number number) {
            countingEnd = number.longValue();
        } else if (rawCountingEnd != null && !rawCountingEnd.toString().isBlank()) {
            countingEnd = Long.valueOf(rawCountingEnd.toString().trim());
        }
        String orderNo = request == null ? null : (String) request.get("orderNo");
        String inStation = request == null ? null : (String) request.get("inStation");
        String outStation = request == null ? null : (String) request.get("outStation");
        return dailyTicketService.markUsed(cardNum, countingEnd, orderNo, inStation, outStation);
    }

    @PostMapping("/ticket/usageLog")
    public DailyTicketBaseResult queryUsageLog(@RequestBody java.util.Map<String, String> request) {
        String cardNum = request == null ? null : request.get("cardNum");
        return dailyTicketService.queryUsageLog(cardNum);
    }
}
