package com.chinasofti.huateng.ticket.controller.ci.app;

import com.chinasofti.huateng.ticket.service.TicketTransService;
import com.chinasofti.huateng.ticket.model.app.RequestTransListReqDTO;
import com.chinasofti.huateng.ticket.model.app.RequestTransListResult;
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
    public RequestTransListResult requestTransList(@RequestBody RequestTransListReqDTO request) {
        log.info("IF8A-05 请求查询交易记录,请求参数: {}", request);
        return ticketTransService.requestTransList(request);
    }
}
