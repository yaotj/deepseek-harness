package com.chinasofti.huateng.ticket.controller.ci.app;

import com.alibaba.fastjson.JSON;
import com.chinasofti.huateng.model.app.QueryUserItineraryReqDTO;
import com.chinasofti.huateng.model.app.QueryUserItineraryResult;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultRespDTO;
import com.chinasofti.huateng.model.ticket.QueryFirstEntryTxnReqDTO;
import com.chinasofti.huateng.model.ticket.QueryFirstEntryTxnResult;
import com.chinasofti.huateng.model.ticket.QueryStatusReqDTO;
import com.chinasofti.huateng.model.ticket.QueryStatusRespDTO;
import com.chinasofti.huateng.model.ticket.RegisterRideStatusReqDTO;
import com.chinasofti.huateng.model.ticket.RegisterRideStatusRespDTO;
import com.chinasofti.huateng.ticket.gate.AgmRideStatusService;
import com.chinasofti.huateng.ticket.entrytxn.EntryTxnQueryService;
import com.chinasofti.huateng.ticket.ridestatus.TicketRideStatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** ticket-server APP 侧接口控制器。 */
@RestController
@RequestMapping("/ci/app")
public class TicketRideStatusController {
    private static final Logger log = LoggerFactory.getLogger(TicketRideStatusController.class);

    private final TicketRideStatusService ticketRideStatusService;
    private final AgmRideStatusService agmRideStatusService;
    private final EntryTxnQueryService entryTxnQueryService;

    public TicketRideStatusController(TicketRideStatusService ticketRideStatusService,
                                      AgmRideStatusService agmRideStatusService,
                                      EntryTxnQueryService entryTxnQueryService) {
        this.ticketRideStatusService = ticketRideStatusService;
        this.agmRideStatusService = agmRideStatusService;
        this.entryTxnQueryService = entryTxnQueryService;
    }

    /** 开户成功后注册用户乘车状态。 */
    @PostMapping("/registerRideStatus")
    public RegisterRideStatusRespDTO registerRideStatus(@RequestBody RegisterRideStatusReqDTO request) {
        log.info("接收注册用户乘车状态请求, cardId={}", request == null ? null : request.getCardId());
        return ticketRideStatusService.registerRideStatus(request);
    }

    /** IF1A-04 查询乘车码状态（闸机侧票卡状态查询）。 */
    @PostMapping("/queryQrCodeStatus")
    public QueryStatusRespDTO queryQrCodeStatus(@RequestBody QueryStatusReqDTO request) {
        log.info("获取用户乘车状态，请求参数：{}", JSON.toJSONString(request));
        return agmRideStatusService.queryQrCodeStatus(request);
    }

    /** IF1A-01 闸机检票通知。 */
    @PostMapping("/notiVerifyResult")
    public NotifyVerifyResultRespDTO notifyVerifyResult(@RequestBody NotifyVerifyResultReqDTO request) {
        log.info("IF1A-01 闸机检票通知，请求参数：{}", JSON.toJSONString(request));
        return agmRideStatusService.notifyVerifyResult(request);
    }

    /** IF8A-29 查询用户上次行程。 */
    @PostMapping("/queryUserItinerary")
    public QueryUserItineraryResult queryUserItinerary(@RequestBody QueryUserItineraryReqDTO request) {
        log.info("IF8A-29 查询用户上次行程，请求参数：{}", JSON.toJSONString(request));
        return ticketRideStatusService.queryUserItinerary(request);
    }

    /** 查询最近一次进站设备编号。 */
    @GetMapping("/queryEntryDevice")
    public String queryEntryDevice(@RequestParam String cardId) {
        log.info("查询进站设备, cardId={}", cardId);
        return entryTxnQueryService.queryEntryDevice(cardId);
    }

    /** 查询同序列号首笔进站交易（{@code gate-txn-pay-server} 离线码出站重算票价用）。 */
    @PostMapping("/queryFirstEntryTxn")
    public QueryFirstEntryTxnResult queryFirstEntryTxn(@RequestBody QueryFirstEntryTxnReqDTO request) {
        log.info("查询首笔进站交易，请求参数：{}", JSON.toJSONString(request));
        return entryTxnQueryService.queryFirstEntryTxn(request);
    }
}
