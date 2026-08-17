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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * APP 日票接口入口（/ci/app）。
 *
 * <p>本 Controller 只负责承接 APP 公共 FormData 报文、解析 bizData 并转发到 daily-ticket-server，
 * 日票订单、支付、激活、退款和 ACC 通知状态由 daily-ticket-server 维护。</p>
 */
@RestController
@RequestMapping("/ci/app")
public class AppDailyTicketController extends BaseAppController {
    private static final Logger log = LoggerFactory.getLogger(AppDailyTicketController.class);

    private final DailyTicketAppService dailyTicketAppService;

    public AppDailyTicketController(DailyTicketAppService dailyTicketAppService) {
        this.dailyTicketAppService = dailyTicketAppService;
    }

    /**
     * IF8A-60 日票下单。
     */
    @PostMapping("/dailyTicket/requestOrder")
    public DailyTicketOrderResult requestCountingOrder(@ModelAttribute CommonFormRequest request) {
        log.info("IF8A-60 日票下单, request={}", request);
        DailyTicketOrderReqDTO bizData = parseBizData(request, DailyTicketOrderReqDTO.class);
        return dailyTicketAppService.requestCountingOrder(bizData);
    }

    /**
     * IF8A-61 日票支付。
     */
    @PostMapping("/dailyTicket/payment/requestPay")
    public DailyTicketPayResult requestPay(@ModelAttribute CommonFormRequest request) {
        log.info("IF8A-61 日票支付, request={}", request);
        DailyTicketPayReqDTO bizData = parseBizData(request, DailyTicketPayReqDTO.class);
        return dailyTicketAppService.requestPay(bizData);
    }

    /**
     * IF8A-62 日票支付结果查询。
     */
    @PostMapping("/dailyTicket/payment/requestPayResult")
    public DailyTicketPayQueryResult requestPayResult(@ModelAttribute CommonFormRequest request) {
        log.info("IF8A-62 日票支付结果查询, request={}", request);
        DailyTicketOrderNoReqDTO bizData = parseBizData(request, DailyTicketOrderNoReqDTO.class);
        return dailyTicketAppService.requestPayResult(bizData);
    }

    /**
     * IF8A-64 日票退款。
     */
    @PostMapping("/dailyTicket/payment/requestRefundTicket")
    public DailyTicketRefundResult requestRefundTicket(@ModelAttribute CommonFormRequest request) {
        log.info("IF8A-64 日票退款, request={}", request);
        DailyTicketOrderNoReqDTO bizData = parseBizData(request, DailyTicketOrderNoReqDTO.class);
        return dailyTicketAppService.requestRefundTicket(bizData);
    }

    /**
     * IF8A-65 日票取消订单。
     */
    @PostMapping("/dailyTicket/cancelOrder")
    public DailyTicketBaseResult cancelOrder(@ModelAttribute CommonFormRequest request) {
        log.info("IF8A-65 日票取消订单, request={}", request);
        DailyTicketOrderNoReqDTO bizData = parseBizData(request, DailyTicketOrderNoReqDTO.class);
        return dailyTicketAppService.cancelOrder(bizData);
    }

    /**
     * IF8A-67 日票激活。
     */
    @PostMapping("/dailyTicket/updateTicket")
    public DailyTicketBaseResult updateTicket(@ModelAttribute CommonFormRequest request) {
        log.info("IF8A-67 日票激活, request={}", request);
        DailyTicketActivateReqDTO bizData = parseBizData(request, DailyTicketActivateReqDTO.class);
        return dailyTicketAppService.updateTicket(bizData);
    }

    /**
     * IF8A-71 通知 ACC 车票已使用。
     */
    @PostMapping("/dailyTicket/updateAndNotice")
    public DailyTicketBaseResult updateAndNotice(@ModelAttribute CommonFormRequest request) {
        log.info("IF8A-71 通知 ACC 车票已使用, request={}", request);
        DailyTicketUsedNoticeReqDTO bizData = parseBizData(request, DailyTicketUsedNoticeReqDTO.class);
        return dailyTicketAppService.updateAndNotice(bizData);
    }

    /**
     * 日票支付结果通知（兼容支付网关通用报文、对象 bizData 和 Base64 编码 bizData）。
     */
    @PostMapping("/dailyTicket/payment/receivePayResult")
    public DailyTicketBaseResult receivePayResult(@RequestBody String requestBody) {
        log.info("日票支付回调原始报文={}", requestBody);
        DailyTicketBaseResult result = dailyTicketAppService.handlePayResultCallback(requestBody);
        log.info("日票支付回调处理结果={}", JSON.toJSONString(result));
        return result;
    }
}
