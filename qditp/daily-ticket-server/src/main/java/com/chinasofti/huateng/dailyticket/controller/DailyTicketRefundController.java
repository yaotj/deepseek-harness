package com.chinasofti.huateng.dailyticket.controller;

import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.dailyticket.page.DailyTicketRefundOrderQuery;
import com.chinasofti.huateng.dailyticket.page.DailyTicketRefundOrderView;
import com.chinasofti.huateng.dailyticket.page.DailyTicketRefundQuery;
import com.chinasofti.huateng.dailyticket.page.DailyTicketRefundView;
import com.chinasofti.huateng.dailyticket.page.TravelTicketSubRefundRequest;
import com.chinasofti.huateng.dailyticket.service.DailyTicketService;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketOrderNoReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketPayQueryResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketRefundResult;
import com.github.pagehelper.PageInfo;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 日票退款运营页面接口。 */
@RestController
@RequestMapping("/page/daily-ticket/refund")
public class DailyTicketRefundController {
    private static final String DAILY_TICKET_ORDER_TYPE = "1";
    private static final String TRAVEL_TICKET_ORDER_TYPE = "2";

    private final DailyTicketService dailyTicketService;

    public DailyTicketRefundController(DailyTicketService dailyTicketService) {
        this.dailyTicketService = dailyTicketService;
    }

    @GetMapping("/orders")
    public ResultVO<PageInfo<DailyTicketRefundOrderView>> pageOrders(DailyTicketRefundOrderQuery query) {
        if (!validPeriod(query == null ? null : query.getBeginTime(), query == null ? null : query.getEndTime())) {
            return ResultMapper.illegalParams("开始时间不能晚于结束时间");
        }
        return dailyTicketService.pageRefundOrders(query);
    }

    @PostMapping("/request")
    public ResultVO<DailyTicketRefundResult> requestRefund(@RequestBody DailyTicketOrderNoReqDTO request) {
        if (request == null || !StringUtils.hasText(request.getOrderNo())) {
            return ResultMapper.illegalParams("日票订单号不能为空");
        }
        defaultOrderType(request);
        DailyTicketRefundResult result = dailyTicketService.requestRefundTicket(request);
        return "0000".equals(result.getRetCode()) ? ResultMapper.ok(result) : ResultMapper.error(result.getRetMsg());
    }

    @PostMapping("/pay-query")
    public ResultVO<DailyTicketPayQueryResult> queryPay(@RequestBody DailyTicketOrderNoReqDTO request) {
        if (request == null || !StringUtils.hasText(request.getOrderNo())) {
            return ResultMapper.illegalParams("日票订单号不能为空");
        }
        defaultOrderType(request);
        DailyTicketPayQueryResult result = dailyTicketService.queryPayTicket(request);
        return "0000".equals(result.getRetCode()) ? ResultMapper.ok(result) : ResultMapper.error(result.getRetMsg());
    }

    @PostMapping("/query")
    public ResultVO<DailyTicketRefundResult> queryRefund(@RequestBody DailyTicketOrderNoReqDTO request) {
        return operateRefund(request, false);
    }

    @PostMapping("/retry")
    public ResultVO<DailyTicketRefundResult> retryRefund(@RequestBody DailyTicketOrderNoReqDTO request) {
        return operateRefund(request, true);
    }

    /** 重提交：支付平台从未受理过的退款单（{@code PLATFORM_REFUND_NO IS NULL}）重新发起退款。 */
    @PostMapping("/resubmit")
    public ResultVO<DailyTicketRefundResult> resubmitRefund(@RequestBody DailyTicketOrderNoReqDTO request) {
        if (request == null || !StringUtils.hasText(request.getOrderNo())) {
            return ResultMapper.illegalParams("日票订单号不能为空");
        }
        defaultOrderType(request);
        DailyTicketRefundResult result = dailyTicketService.resubmitRefundTicket(request);
        return "0000".equals(result.getRetCode()) ? ResultMapper.ok(result) : ResultMapper.error(result.getRetMsg());
    }

    @GetMapping("/records")
    public ResultVO<PageInfo<DailyTicketRefundView>> pageRecords(DailyTicketRefundQuery query) {
        if (!validPeriod(query == null ? null : query.getBeginTime(), query == null ? null : query.getEndTime())) {
            return ResultMapper.illegalParams("开始时间不能晚于结束时间");
        }
        return dailyTicketService.pageRefundRecords(query);
    }

    @GetMapping("/travel/{orderNo}/sub-orders")
    public ResultVO<java.util.List<DailyTicketRefundOrderView>> listTravelSubOrders(@PathVariable String orderNo) {
        return dailyTicketService.listTravelSubRefundOrders(orderNo);
    }

    @PostMapping("/travel/sub-refund")
    public ResultVO<DailyTicketRefundResult> requestTravelSubRefund(@RequestBody TravelTicketSubRefundRequest request) {
        DailyTicketRefundResult result = dailyTicketService.requestTravelSubRefund(request);
        return "0000".equals(result.getRetCode()) ? ResultMapper.ok(result) : ResultMapper.error(result.getRetMsg());
    }

    private boolean validPeriod(java.util.Date beginTime, java.util.Date endTime) {
        return beginTime == null || endTime == null || !beginTime.after(endTime);
    }

    /** 页面操作统一校验订单号，未传订单类型时兼容旧页面按日票处理。 */
    private ResultVO<DailyTicketRefundResult> operateRefund(DailyTicketOrderNoReqDTO request, boolean retry) {
        if (request == null || !StringUtils.hasText(request.getOrderNo())) {
            return ResultMapper.illegalParams("日票订单号不能为空");
        }
        defaultOrderType(request);
        DailyTicketRefundResult result = retry
                ? dailyTicketService.retryRefundTicket(request)
                : dailyTicketService.queryRefundTicket(request);
        return "0000".equals(result.getRetCode()) ? ResultMapper.ok(result) : ResultMapper.error(result.getRetMsg());
    }

    private void defaultOrderType(DailyTicketOrderNoReqDTO request) {
        if (!StringUtils.hasText(request.getOrderType())) {
            request.setOrderType(DAILY_TICKET_ORDER_TYPE);
        }
    }
}
