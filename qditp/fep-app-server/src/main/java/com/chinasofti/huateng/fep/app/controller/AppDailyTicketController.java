package com.chinasofti.huateng.fep.app.controller;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.model.app.ItpCommonFormRequest;
import com.chinasofti.huateng.fep.app.service.DailyTicketAppService;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketActivateReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketBaseResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketFreeOrderReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketFreeOrderResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketOrderNoReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketOrderReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketOrderResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketPayQueryResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketPayReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketPayResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketRefundCallbackReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketRefundResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketSyncOrderReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketUsedNoticeReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.TravelTicketOrderReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.TravelTicketOrderResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class AppDailyTicketController extends BaseAppController {
    private static final Logger log = LoggerFactory.getLogger(AppDailyTicketController.class);

    private final DailyTicketAppService dailyTicketAppService;

    public AppDailyTicketController(DailyTicketAppService dailyTicketAppService) {
        this.dailyTicketAppService = dailyTicketAppService;
    }

    @PostMapping({"/ci/app/dailyTicket/requestOrder", "/app/dailyTicket/requestOrder", "/app/requestCountingOrder",
            "/app/ticket/requestOrder"})
    public DailyTicketOrderResult requestOrder(@ModelAttribute ItpCommonFormRequest request) {
        log.info("IF8A-60 日票下单, request={}", request);
        return dailyTicketAppService.requestCountingOrder(parseBizData(request, DailyTicketOrderReqDTO.class));
    }


    @PostMapping({"/ci/app/dailyTicket/requestTravelOrder", "/app/dailyTicket/requestTravelOrder",
            "/app/ticket/requestTravelOrder"})
    public TravelTicketOrderResult requestTravelOrder(@ModelAttribute ItpCommonFormRequest request) {
        log.info("IF8A-70 旅游票下单, request={}", request);
        return dailyTicketAppService.requestTravelOrder(parseBizData(request, TravelTicketOrderReqDTO.class));
    }


    @PostMapping({"/app/ticket/requestOrderFree", "/ci/app/dailyTicket/requestOrderFree",
            "/app/dailyTicket/requestOrderFree"})
    public DailyTicketFreeOrderResult requestOrderFree(@ModelAttribute ItpCommonFormRequest request) {
        log.info("IF8A-73 免费票请求下单, request={}", request);
        return dailyTicketAppService.requestOrderFree(parseBizData(request, DailyTicketFreeOrderReqDTO.class));
    }


    @PostMapping({"/app/ticket/syncOrder", "/ci/app/dailyTicket/syncOrder", "/app/dailyTicket/syncOrder"})
    public DailyTicketBaseResult syncOrder(@ModelAttribute ItpCommonFormRequest request) {
        log.info("IF8A-72 小程序票状态同步, request={}", request);
        return dailyTicketAppService.syncOrder(parseBizData(request, DailyTicketSyncOrderReqDTO.class));
    }


    @PostMapping({"/ci/app/dailyTicket/payment/requestPay", "/app/dailyTicket/payment/requestPay",
            "/app/payment/requestPay", "/app/ticket/payment/requestPay"})
    public DailyTicketPayResult pay(@ModelAttribute ItpCommonFormRequest request) {
        log.info("IF8A-61 日票支付, request={}", request);
        return dailyTicketAppService.requestPay(parseBizData(request, DailyTicketPayReqDTO.class));
    }


    @PostMapping({"/ci/app/dailyTicket/payment/requestPayResult", "/app/dailyTicket/payment/requestPayResult",
            "/app/payment/requestPayResult", "/app/ticket/payment/requestPayResult",
            "/payment/requestPayResult"})
    public DailyTicketPayQueryResult queryPayResult(@ModelAttribute ItpCommonFormRequest request) {
        log.info("IF8A-62 日票支付结果查询, request={}", request);
        return dailyTicketAppService.requestPayResult(parseBizData(request, DailyTicketOrderNoReqDTO.class));
    }


    @PostMapping({"/ci/app/dailyTicket/payment/requestRefundTicket", "/app/dailyTicket/payment/requestRefundTicket",
            "/app/payment/requestRefundTicket", "/app/ticket/payment/requestRefundTicket"})
    public DailyTicketRefundResult requestRefund(@ModelAttribute ItpCommonFormRequest request) {
        log.info("IF8A-64 日票退款, request={}", request);
        return dailyTicketAppService.requestRefundTicket(parseBizData(request, DailyTicketOrderNoReqDTO.class));
    }

    @PostMapping({"/ci/app/dailyTicket/cancelOrder", "/app/dailyTicket/cancelOrder", "/app/ticket/cancelOrder"})
    public DailyTicketBaseResult cancelOrder(@ModelAttribute ItpCommonFormRequest request) {
        log.info("IF8A-65 日票取消订单, request={}", request);
        return dailyTicketAppService.cancelOrder(parseBizData(request, DailyTicketOrderNoReqDTO.class));
    }

    @PostMapping({"/ci/app/dailyTicket/updateTicket", "/app/dailyTicket/updateTicket", "/app/ticket/updateTicket"})
    public DailyTicketBaseResult activateTicket(@ModelAttribute ItpCommonFormRequest request) {
        log.info("IF8A-67 日票激活, request={}", request);
        return dailyTicketAppService.updateTicket(parseBizData(request, DailyTicketActivateReqDTO.class));
    }

    @PostMapping({"/ci/app/dailyTicket/updateAndNotice", "/app/dailyTicket/updateAndNotice",
            "/app/ticket/updateAndNotice"})
    public DailyTicketBaseResult notifyAccUsed(@ModelAttribute ItpCommonFormRequest request) {
        log.info("IF8A-71 通知 ACC 车票已使用, request={}", request);
        return dailyTicketAppService.updateAndNotice(parseBizData(request, DailyTicketUsedNoticeReqDTO.class));
    }


    @PostMapping({"/ci/app/dailyTicket/payment/receivePayResult", "/app/dailyTicket/payment/receivePayResult",
            "/app/payment/receivePayResult", "/app/ticket/payment/receivePayResult"})
    public DailyTicketBaseResult receivePayNotify(@RequestBody String requestBody) {
        log.info("日票支付回调原始报文={}", requestBody);
        DailyTicketBaseResult result = dailyTicketAppService.handlePayResultCallback(requestBody);
        log.info("日票支付回调处理结果={}", JSON.toJSONString(result));
        return result;
    }


    @PostMapping({"/ci/app/dailyTicket/payment/receiveRefundResult", "/app/dailyTicket/payment/receiveRefundResult",
            "/app/payment/receiveRefundResult", "/app/ticket/payment/receiveRefundResult"})
    public DailyTicketBaseResult receiveRefundNotify(@RequestBody String requestBody) {
        log.info("日票退款结果回调原始报文={}", requestBody);
        DailyTicketRefundCallbackReqDTO callbackRequest =
                parseCallbackBody(requestBody, DailyTicketRefundCallbackReqDTO.class);
        if (callbackRequest == null) {
            callbackRequest = parseBizData(JSON.parseObject(requestBody, ItpCommonFormRequest.class),
                    DailyTicketRefundCallbackReqDTO.class);
        }
        DailyTicketBaseResult result = dailyTicketAppService.receiveRefundResult(callbackRequest);
        log.info("日票退款结果回调处理结果={}", JSON.toJSONString(result));
        return result;
    }
}
