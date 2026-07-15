package com.chinasofti.huateng.ticket.controller.ci.agm;

import com.alibaba.fastjson.JSON;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultRespDTO;
import com.chinasofti.huateng.ticket.service.TicketRideStatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ticket-server AGM 侧接口控制器。
 */
@RestController
@RequestMapping("/ci/agm")
public class TicketAgmController {
    private static final Logger log = LoggerFactory.getLogger(TicketAgmController.class);

    private final TicketRideStatusService ticketRideStatusService;

    public TicketAgmController(TicketRideStatusService ticketRideStatusService) {
        this.ticketRideStatusService = ticketRideStatusService;
    }

    /**
     * IF1A-01 闸机检票通知。
     *
     * @param request 闸机检票通知业务参数
     * @return 处理结果
     */
    @PostMapping("/notiVerifyResult")
    public NotifyVerifyResultRespDTO notifyVerifyResult(@RequestBody NotifyVerifyResultReqDTO request) {
        log.info("IF1A-01 闸机检票通知，请求参数：{}", JSON.toJSONString(request));
        return ticketRideStatusService.notifyVerifyResult(request);
    }
}
