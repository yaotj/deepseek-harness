package com.chinasofti.huateng.dailyticket.controller;

import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.dailyticket.page.DailyTicketRefundOrderQuery;
import com.chinasofti.huateng.dailyticket.page.DailyTicketRefundOrderView;
import com.chinasofti.huateng.dailyticket.page.DailyTicketRefundQuery;
import com.chinasofti.huateng.dailyticket.page.DailyTicketRefundView;
import com.chinasofti.huateng.dailyticket.service.DailyTicketService;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketOrderNoReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketPayQueryResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketRefundResult;
import com.github.pagehelper.PageInfo;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 日票退款运营页面接口。
 *
 * <p>与 APP 的日票业务接口分离，仅承载后台订单检索、退款发起和退款记录查询。</p>
 */
@RestController
@RequestMapping("/page/daily-ticket/refund")
public class DailyTicketRefundController {
    private static final String DAILY_TICKET_ORDER_TYPE = "1";

    private final DailyTicketService dailyTicketService;

    public DailyTicketRefundController(DailyTicketService dailyTicketService) {
        this.dailyTicketService = dailyTicketService;
    }

    @GetMapping("/orders")
    public ResultVO<PageInfo<DailyTicketRefundOrderView>> pageOrders(DailyTicketRefundOrderQuery query) {
        // 日期条件作用于订单创建时间，避免页面传入倒置区间导致全量误查。
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
        // 页面只允许处理日票，订单类型不信任前端传值。
        request.setOrderType(DAILY_TICKET_ORDER_TYPE);
        DailyTicketRefundResult result = dailyTicketService.requestRefundTicket(request);
        return "0000".equals(result.getRetCode()) ? ResultMapper.ok(result) : ResultMapper.error(result.getRetMsg());
    }

    @PostMapping("/pay-query")
    public ResultVO<DailyTicketPayQueryResult> queryPay(@RequestBody DailyTicketOrderNoReqDTO request) {
        if (request == null || !StringUtils.hasText(request.getOrderNo())) {
            return ResultMapper.illegalParams("日票订单号不能为空");
        }
        // 页面不透传订单类型，避免被误用于非日票订单查询。
        request.setOrderType(DAILY_TICKET_ORDER_TYPE);
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

    @GetMapping("/records")
    public ResultVO<PageInfo<DailyTicketRefundView>> pageRecords(DailyTicketRefundQuery query) {
        // 退款记录的日期条件作用于退款申请创建时间。
        if (!validPeriod(query == null ? null : query.getBeginTime(), query == null ? null : query.getEndTime())) {
            return ResultMapper.illegalParams("开始时间不能晚于结束时间");
        }
        return dailyTicketService.pageRefundRecords(query);
    }

    private boolean validPeriod(java.util.Date beginTime, java.util.Date endTime) {
        return beginTime == null || endTime == null || !beginTime.after(endTime);
    }

    /** 页面操作统一校验订单号并固定为日票订单，避免接口参数被篡改。 */
    private ResultVO<DailyTicketRefundResult> operateRefund(DailyTicketOrderNoReqDTO request, boolean retry) {
        if (request == null || !StringUtils.hasText(request.getOrderNo())) {
            return ResultMapper.illegalParams("日票订单号不能为空");
        }
        request.setOrderType(DAILY_TICKET_ORDER_TYPE);
        DailyTicketRefundResult result = retry
                ? dailyTicketService.retryRefundTicket(request)
                : dailyTicketService.queryRefundTicket(request);
        return "0000".equals(result.getRetCode()) ? ResultMapper.ok(result) : ResultMapper.error(result.getRetMsg());
    }
}
