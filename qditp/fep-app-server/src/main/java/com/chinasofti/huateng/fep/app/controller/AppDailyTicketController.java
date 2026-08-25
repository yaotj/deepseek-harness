package com.chinasofti.huateng.fep.app.controller;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.fep.app.model.CommonFormRequest;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * APP 日票接口入口。
 *
 * <p>本 Controller 只负责承接 APP 公共 FormData 报文、解析 bizData 并转发到 daily-ticket-server。
 * 同时支持 {@code /ci/app} 和 {@code /app} 两条路径。</p>
 */
@RestController
public class AppDailyTicketController extends BaseAppController {
    private static final Logger log = LoggerFactory.getLogger(AppDailyTicketController.class);

    private final DailyTicketAppService dailyTicketAppService;

    public AppDailyTicketController(DailyTicketAppService dailyTicketAppService) {
        this.dailyTicketAppService = dailyTicketAppService;
    }

    @PostMapping({"/ci/app/dailyTicket/requestOrder", "/app/dailyTicket/requestOrder", "/app/requestCountingOrder"})
    public DailyTicketOrderResult requestOrder(@ModelAttribute CommonFormRequest request) {
        log.info("IF8A-60 日票下单, request={}", request);
        return dailyTicketAppService.requestCountingOrder(parseBizData(request, DailyTicketOrderReqDTO.class));
    }

    @PostMapping({"/ci/app/dailyTicket/payment/requestPay", "/app/dailyTicket/payment/requestPay"})
    public DailyTicketPayResult pay(@ModelAttribute CommonFormRequest request) {
        log.info("IF8A-61 日票支付, request={}", request);
        return dailyTicketAppService.requestPay(parseBizData(request, DailyTicketPayReqDTO.class));
    }

    @PostMapping({"/ci/app/dailyTicket/payment/requestPayResult", "/app/dailyTicket/payment/requestPayResult"})
    public DailyTicketPayQueryResult queryPayResult(@ModelAttribute CommonFormRequest request) {
        log.info("IF8A-62 日票支付结果查询, request={}", request);
        return dailyTicketAppService.requestPayResult(parseBizData(request, DailyTicketOrderNoReqDTO.class));
    }

    @PostMapping({"/ci/app/dailyTicket/payment/requestRefundTicket", "/app/dailyTicket/payment/requestRefundTicket"})
    public DailyTicketRefundResult requestRefund(@ModelAttribute CommonFormRequest request) {
        log.info("IF8A-64 日票退款, request={}", request);
        return dailyTicketAppService.requestRefundTicket(parseBizData(request, DailyTicketOrderNoReqDTO.class));
    }

    @PostMapping({"/ci/app/dailyTicket/cancelOrder", "/app/dailyTicket/cancelOrder"})
    public DailyTicketBaseResult cancelOrder(@ModelAttribute CommonFormRequest request) {
        log.info("IF8A-65 日票取消订单, request={}", request);
        return dailyTicketAppService.cancelOrder(parseBizData(request, DailyTicketOrderNoReqDTO.class));
    }

    @PostMapping({"/ci/app/dailyTicket/updateTicket", "/app/dailyTicket/updateTicket"})
    public DailyTicketBaseResult activateTicket(@ModelAttribute CommonFormRequest request) {
        log.info("IF8A-67 日票激活, request={}", request);
        return dailyTicketAppService.updateTicket(parseBizData(request, DailyTicketActivateReqDTO.class));
    }

    @PostMapping({"/ci/app/dailyTicket/updateAndNotice", "/app/dailyTicket/updateAndNotice"})
    public DailyTicketBaseResult notifyAccUsed(@ModelAttribute CommonFormRequest request) {
        log.info("IF8A-71 通知 ACC 车票已使用, request={}", request);
        return dailyTicketAppService.updateAndNotice(parseBizData(request, DailyTicketUsedNoticeReqDTO.class));
    }

    @PostMapping({"/ci/app/dailyTicket/payment/receivePayResult", "/app/dailyTicket/payment/receivePayResult"})
    public DailyTicketBaseResult receivePayNotify(@RequestBody String requestBody) {
        log.info("日票支付回调原始报文={}", requestBody);
        DailyTicketBaseResult result = dailyTicketAppService.handlePayResultCallback(requestBody);
        log.info("日票支付回调处理结果={}", JSON.toJSONString(result));
        return result;
    }
}
