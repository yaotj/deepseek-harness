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
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketRefundCallbackReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketRefundResult;
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

/** 日票服务内部业务接口。 */
@RestController
@RequestMapping("/ci/daily-ticket")
public class DailyTicketController {
    private final DailyTicketService dailyTicketService;

    public DailyTicketController(DailyTicketService dailyTicketService) {
        this.dailyTicketService = dailyTicketService;
    }

    /** IF8A-60 日票下单。 */
    @PostMapping("/requestCountingOrder")
    public DailyTicketOrderResult requestCountingOrder(@RequestBody DailyTicketOrderReqDTO request) {
        return dailyTicketService.requestCountingOrder(request);
    }

    /** IF8A-70 旅游票下单。旅游票为聚合单，内含多张日票。 */
    @PostMapping("/requestTravelOrder")
    public TravelTicketOrderResult requestTravelOrder(@RequestBody TravelTicketOrderReqDTO request) {
        return dailyTicketService.requestTravelOrder(request);
    }

    /** IF8A-61 日票支付。 */
    @PostMapping("/payment/requestPay")
    public DailyTicketPayResult requestPay(@RequestBody DailyTicketPayReqDTO request) {
        return dailyTicketService.requestPay(request);
    }

    /** IF8A-62 日票支付结果查询。 */
    @PostMapping("/payment/requestPayResult")
    public DailyTicketPayQueryResult requestPayResult(@RequestBody DailyTicketOrderNoReqDTO request) {
        return dailyTicketService.requestPayResult(request);
    }

    /** IF8A-64 日票退款。 */
    @PostMapping("/payment/requestRefundTicket")
    public DailyTicketRefundResult requestRefundTicket(@RequestBody DailyTicketOrderNoReqDTO request) {
        return dailyTicketService.requestRefundTicket(request);
    }

    /** IF8A-65 日票取消订单。 */
    @PostMapping("/ticket/cancelOrder")
    public DailyTicketBaseResult cancelOrder(@RequestBody DailyTicketOrderNoReqDTO request) {
        return dailyTicketService.cancelOrder(request);
    }

    /** IF8A-67 日票激活。 */
    @PostMapping("/ticket/updateTicket")
    public DailyTicketBaseResult updateTicket(@RequestBody DailyTicketActivateReqDTO request) {
        return dailyTicketService.updateTicket(request);
    }

    /** IF8A-71 通知ACC车票已使用。 */
    @PostMapping("/ticket/updateAndNotice")
    public DailyTicketBaseResult updateAndNotice(@RequestBody DailyTicketUsedNoticeReqDTO request) {
        return dailyTicketService.updateAndNotice(request);
    }

    /** 支付结果回调内部入口。 */
    @PostMapping("/payment/receivePayResult")
    public DailyTicketBaseResult receivePayResult(@RequestBody DailyTicketPayCallbackReqDTO request) {
        return dailyTicketService.receivePayResult(request);
    }

    /** 退款结果回调内部入口（支付中心网关 §3.3 → fep-app-server → 本接口）。 */
    @PostMapping("/payment/receiveRefundResult")
    public DailyTicketBaseResult receiveRefundResult(@RequestBody DailyTicketRefundCallbackReqDTO request) {
        return dailyTicketService.receiveRefundResult(request);
    }

    /** 查询日票票实例信息（ticketCode、actualTimes）。 */
    @PostMapping("/queryDailyTicketInfo")
    public QueryDailyTicketInfoResult queryDailyTicketInfo(@RequestBody QueryDailyTicketInfoReqDTO request) {
        return dailyTicketService.queryDailyTicketInfo(request);
    }

    /** 按票号查日票购票支付信息（payTradeOrderNo、payOrderNoDate、payChannelCode）。 */
    @PostMapping("/queryDailyTicketPayInfo")
    public QueryDailyTicketPayInfoResult queryDailyTicketPayInfo(@RequestBody QueryDailyTicketPayInfoReqDTO request) {
        return dailyTicketService.queryDailyTicketPayInfo(request);
    }

    /**
     * 日票进站校验（闸机入口调用）。
     * 校验有效期、未完成出站、计次票次数（不扣减）。
     */
    @PostMapping("/entry/check")
    public DailyTicketBaseResult entryCheck(@RequestBody java.util.Map<String, String> request) {
        String cardNum = request == null ? null : request.get("cardNum");
        return dailyTicketService.validateEntryCheck(cardNum);
    }

    /**
     * 日票乘车可用性查询（拉码 IF8A-03 前置，只读）。
     * 只判定有效期 / 次数 / 退款占用，不推进任何状态；闸机侧 entry/check 那道权威校验 NEVER 撤。
     */
    @PostMapping("/ticket/rideAvailability")
    public DailyTicketBaseResult rideAvailability(@RequestBody java.util.Map<String, String> request) {
        String cardNum = request == null ? null : request.get("cardNum");
        return dailyTicketService.checkRideAvailability(cardNum);
    }

    /**
     * 日票出站处理（闸机出站时调用）。
     * 扣减计次票次数（下限为0），推进票状态。
     */
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

    /** 查询日票扣次使用明细（按卡号，时间倒序）。 */
    @PostMapping("/ticket/usageLog")
    public DailyTicketBaseResult queryUsageLog(@RequestBody java.util.Map<String, String> request) {
        String cardNum = request == null ? null : request.get("cardNum");
        return dailyTicketService.queryUsageLog(cardNum);
    }
}
