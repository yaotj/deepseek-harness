package com.chinasofti.huateng.fep.app.controller;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.fep.app.model.CommonFormRequest;
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
import com.chinasofti.huateng.fep.app.service.DailyTicketAppService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * APP日票接口前置入口。
 *
 * <p>本Controller只负责承接APP公共FormData报文、解析bizData并转发到daily-ticket-server，
 * 日票订单、支付、激活、退款和ACC通知状态由daily-ticket-server维护。</p>
 */
@RestController
@RequestMapping("/app")
public class FepAppDailyTicketController {
    private static final Logger log = LoggerFactory.getLogger(FepAppDailyTicketController.class);

    private final DailyTicketAppService dailyTicketAppService;

    public FepAppDailyTicketController(DailyTicketAppService dailyTicketAppService) {
        this.dailyTicketAppService = dailyTicketAppService;
    }

    /**
     * IF8A-60 日票下单。
     *
     * @param request APP公共FormData请求，bizData为DailyTicketOrderReqDTO JSON
     * @return 日票订单号
     */
    @PostMapping("/requestCountingOrder")
    public DailyTicketOrderResult requestCountingOrder(@ModelAttribute CommonFormRequest request) {
        log.info("IF8A-60 日票下单, request={}", request);
        DailyTicketOrderReqDTO bizData = JSON.parseObject(request.getBizData(), DailyTicketOrderReqDTO.class);
        return dailyTicketAppService.requestCountingOrder(bizData);
    }

    /**
     * IF8A-61 日票支付。
     *
     * <p>channelType在daily-ticket-server中转换为支付scene：1-app，2-wap。</p>
     *
     * @param request APP公共FormData请求，bizData为DailyTicketPayReqDTO JSON
     * @return 支付插件/收银台参数
     */
    @PostMapping("/payment/requestPay")
    public DailyTicketPayResult requestPay(@ModelAttribute CommonFormRequest request) {
        log.info("IF8A-61 日票支付, request={}", request);
        DailyTicketPayReqDTO bizData = JSON.parseObject(request.getBizData(), DailyTicketPayReqDTO.class);
        return dailyTicketAppService.requestPay(bizData);
    }

    /**
     * IF8A-62 日票支付结果查询。
     *
     * @param request APP公共FormData请求，bizData为DailyTicketOrderNoReqDTO JSON
     * @return 支付结果
     */
    @PostMapping("/payment/requestPayResult")
    public DailyTicketPayQueryResult requestPayResult(@ModelAttribute CommonFormRequest request) {
        log.info("IF8A-62 日票支付结果查询, request={}", request);
        DailyTicketOrderNoReqDTO bizData = JSON.parseObject(request.getBizData(), DailyTicketOrderNoReqDTO.class);
        return dailyTicketAppService.requestPayResult(bizData);
    }

    /**
     * IF8A-64 日票退款。
     *
     * @param request APP公共FormData请求，bizData为DailyTicketOrderNoReqDTO JSON
     * @return 退款受理/处理结果
     */
    @PostMapping("/payment/requestRefundTicket")
    public DailyTicketRefundResult requestRefundTicket(@ModelAttribute CommonFormRequest request) {
        log.info("IF8A-64 日票退款, request={}", request);
        DailyTicketOrderNoReqDTO bizData = JSON.parseObject(request.getBizData(), DailyTicketOrderNoReqDTO.class);
        return dailyTicketAppService.requestRefundTicket(bizData);
    }

    /**
     * IF8A-65 日票取消订单。
     *
     * @param request APP公共FormData请求，bizData为DailyTicketOrderNoReqDTO JSON
     * @return 通用处理结果
     */
    @PostMapping("/ticket/cancelOrder")
    public DailyTicketBaseResult cancelOrder(@ModelAttribute CommonFormRequest request) {
        log.info("IF8A-65 日票取消订单, request={}", request);
        DailyTicketOrderNoReqDTO bizData = JSON.parseObject(request.getBizData(), DailyTicketOrderNoReqDTO.class);
        return dailyTicketAppService.cancelOrder(bizData);
    }

    /**
     * IF8A-67 日票激活。
     *
     * <p>激活后daily-ticket-server创建日票实例。后续生码时业务卡类型与码体票种分离，
     * 码体车票类型固定为0441。</p>
     *
     * @param request APP公共FormData请求，bizData为DailyTicketActivateReqDTO JSON
     * @return 通用处理结果
     */
    @PostMapping("/ticket/updateTicket")
    public DailyTicketBaseResult updateTicket(@ModelAttribute CommonFormRequest request) {
        log.info("IF8A-67 日票激活, request={}", request);
        DailyTicketActivateReqDTO bizData = JSON.parseObject(request.getBizData(), DailyTicketActivateReqDTO.class);
        return dailyTicketAppService.updateTicket(bizData);
    }

    /**
     * IF8A-71 通知ACC车票已使用。
     *
     * @param request APP公共FormData请求，bizData为DailyTicketUsedNoticeReqDTO JSON
     * @return 通用处理结果
     */
    @PostMapping("/ticket/updateAndNotice")
    public DailyTicketBaseResult updateAndNotice(@ModelAttribute CommonFormRequest request) {
        log.info("IF8A-71 通知ACC车票已使用, request={}", request);
        DailyTicketUsedNoticeReqDTO bizData = JSON.parseObject(request.getBizData(), DailyTicketUsedNoticeReqDTO.class);
        return dailyTicketAppService.updateAndNotice(bizData);
    }
}
