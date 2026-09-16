package com.chinasofti.huateng.ticket.controller.ci.app;

import com.chinasofti.huateng.model.app.RequestTransDetailReqDTO;
import com.chinasofti.huateng.model.app.RequestTransDetailResult;
import com.chinasofti.huateng.model.app.RequestTransStatisticsReqDTO;
import com.chinasofti.huateng.model.app.RequestTransStatisticsResult;
import com.chinasofti.huateng.model.app.QueryTransListReqDTO;
import com.chinasofti.huateng.model.app.RequestTransListResult;
import com.chinasofti.huateng.ticket.query.TicketTransService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * IF8A-05 请求查询交易记录。
 * 对应规范 /ci/app/requestTransList，由 fep-app-server 通过 RPC 调用。
 */
@RestController
@RequestMapping("/ci/app")
public class TicketTransController {
    private static final Logger log = LoggerFactory.getLogger(TicketTransController.class);

    @Autowired
    private TicketTransService ticketTransService;

    /**
     * IF8A-05 请求查询交易记录。
     *
     * @param request 查询交易记录请求参数
     * @return 交易记录列表
     */
    @PostMapping("/requestTransList")
    public RequestTransListResult requestTransList(@RequestBody QueryTransListReqDTO request) {
        log.info("IF8A-05 请求查询交易记录,请求参数: {}", request);
        return ticketTransService.requestTransList(request);
    }

    /**
     * IF8A-41 查询账单统计。
     *
     * @param request 查询账单统计请求参数
     * @return 账单统计结果
     */
    @PostMapping("/requestTransStatistics")
    public RequestTransStatisticsResult requestTransStatistics(@RequestBody RequestTransStatisticsReqDTO request) {
        log.info("IF8A-41 查询账单统计,请求参数: {}", request);
        return ticketTransService.requestTransStatistics(request);
    }

    /**
     * IF8A-34 获取订单详情。
     *
     * @param request 获取订单详情请求参数
     * @return 订单详情
     */
    @PostMapping("/requestTransDetail")
    public RequestTransDetailResult requestTransDetail(@RequestBody RequestTransDetailReqDTO request) {
        log.info("IF8A-34 获取订单详情,请求参数: {}", request);
        return ticketTransService.requestTransDetail(request);
    }
}
