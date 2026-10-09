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

/**
 * APP 日票接口入口。
 */
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

    /**
     * IF8A-70 旅游票下单。
     */
    @PostMapping({"/ci/app/dailyTicket/requestTravelOrder", "/app/dailyTicket/requestTravelOrder",
            "/app/ticket/requestTravelOrder"})
    public TravelTicketOrderResult requestTravelOrder(@ModelAttribute ItpCommonFormRequest request) {
        log.info("IF8A-70 旅游票下单, request={}", request);
        return dailyTicketAppService.requestTravelOrder(parseBizData(request, TravelTicketOrderReqDTO.class));
    }

    /**
     * IF8A-73 免费票请求下单。
     */
    @PostMapping({"/app/ticket/requestOrderFree", "/ci/app/dailyTicket/requestOrderFree",
            "/app/dailyTicket/requestOrderFree"})
    public DailyTicketFreeOrderResult requestOrderFree(@ModelAttribute ItpCommonFormRequest request) {
        log.info("IF8A-73 免费票请求下单, request={}", request);
        return dailyTicketAppService.requestOrderFree(parseBizData(request, DailyTicketFreeOrderReqDTO.class));
    }

    /**
     * IF8A-72 小程序票状态同步。
     */
    @PostMapping({"/app/ticket/syncOrder", "/ci/app/dailyTicket/syncOrder", "/app/dailyTicket/syncOrder"})
    public DailyTicketBaseResult syncOrder(@ModelAttribute ItpCommonFormRequest request) {
        log.info("IF8A-72 小程序票状态同步, request={}", request);
        return dailyTicketAppService.syncOrder(parseBizData(request, DailyTicketSyncOrderReqDTO.class));
    }

    /**
     * if8a_61 日票支付。
     */
    @PostMapping({"/ci/app/dailyTicket/payment/requestPay", "/app/dailyTicket/payment/requestPay",
            "/app/payment/requestPay", "/app/ticket/payment/requestPay"})
    public DailyTicketPayResult pay(@ModelAttribute ItpCommonFormRequest request) {
        log.info("IF8A-61 日票支付, request={}", request);
        return dailyTicketAppService.requestPay(parseBizData(request, DailyTicketPayReqDTO.class));
    }

    /**
     * if8a_62 日票支付结果查询。
     *
     * <p>第 5 个别名 {@code /payment/requestPayResult}（**不带 {@code /app} 前缀**）是 2026-09-22 按线上实证补的：
     * APP 打的是 {@code /fep-app/payment/requestPayResult}，网关 rewrite 掉 {@code /fep-app} 后落到
     * 本服务的 {@code /payment/requestPayResult}，而当时只有带 {@code /app} 的四个别名 ⇒ 无 handler ⇒
     * 日志 {@code No static resource payment/requestPayResult} ⇒ 被全局异常处理器包成
     * HTTP 200 + UUID {@code retCode}，APP 页面只显示「查询失败」（订单 {@code 0E202609221650330001}，
     * 16:53:05 实测）。
     *
     * <p><b>NEVER 因为「看着像笔误」就删掉这个裸别名</b> —— 它对应的是 APP 侧真实在用的 URL。
     * 反过来也 <b>NEVER 给本类其余方法批量补裸别名</b>：同族的 {@code requestPay} /
     * {@code requestRefundTicket} / {@code cancelOrder} 在日志里从未出现过裸路径形态，
     * 无实证就加等于继续放大 URL 别名蔓延。再遇到同类 404 时 MUST 先去 fep-app 日志取
     * {@code No static resource} 那行原文，按实际 path 补。
     */
    @PostMapping({"/ci/app/dailyTicket/payment/requestPayResult", "/app/dailyTicket/payment/requestPayResult",
            "/app/payment/requestPayResult", "/app/ticket/payment/requestPayResult",
            "/payment/requestPayResult"})
    public DailyTicketPayQueryResult queryPayResult(@ModelAttribute ItpCommonFormRequest request) {
        log.info("IF8A-62 日票支付结果查询, request={}", request);
        return dailyTicketAppService.requestPayResult(parseBizData(request, DailyTicketOrderNoReqDTO.class));
    }

    /**
     * IF8A-64 日票退款。
     */
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

    /**
     * 日票支付回调（支付网关 -> fep-app -> daily-ticket-server）。
     */
    @PostMapping({"/ci/app/dailyTicket/payment/receivePayResult", "/app/dailyTicket/payment/receivePayResult",
            "/app/payment/receivePayResult", "/app/ticket/payment/receivePayResult"})
    public DailyTicketBaseResult receivePayNotify(@RequestBody String requestBody) {
        log.info("日票支付回调原始报文={}", requestBody);
        DailyTicketBaseResult result = dailyTicketAppService.handlePayResultCallback(requestBody);
        log.info("日票支付回调处理结果={}", JSON.toJSONString(result));
        return result;
    }

    /**
     * 日票退款结果回调（支付中心 -> fep-app -> daily-ticket-server）。
     */
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
