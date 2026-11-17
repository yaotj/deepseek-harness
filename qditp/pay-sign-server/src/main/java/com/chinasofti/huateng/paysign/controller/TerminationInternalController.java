package com.chinasofti.huateng.paysign.controller;

import com.chinasofti.huateng.model.paysign.ProcessTerminationReqDTO;
import com.chinasofti.huateng.model.paysign.ProcessTerminationRespDTO;
import com.chinasofti.huateng.paysign.model.request.CheckFailedOrdersReqDTO;
import com.chinasofti.huateng.paysign.model.request.ExecuteTerminationReqDTO;
import com.chinasofti.huateng.paysign.model.request.NotifyTerminationFailedReqDTO;
import com.chinasofti.huateng.paysign.model.response.BaseRespDTO;
import com.chinasofti.huateng.paysign.model.response.CheckFailedOrdersRespDTO;
import com.chinasofti.huateng.model.paysign.CompensateNotifyRespDTO;
import com.chinasofti.huateng.paysign.service.TerminationInternalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/termination")
public class TerminationInternalController {

    private static final Logger log = LoggerFactory.getLogger(TerminationInternalController.class);

    private final TerminationInternalService terminationInternalService;

    public TerminationInternalController(TerminationInternalService terminationInternalService) {
        this.terminationInternalService = terminationInternalService;
    }

    @PostMapping("/process")
    public ProcessTerminationRespDTO processTermination(
            @RequestBody(required = false) ProcessTerminationReqDTO request) {
        log.info("收到解约申请批处理请求, request={}", request);
        return terminationInternalService.processTermination(request);
    }

    @PostMapping("/compensateNotify")
    public CompensateNotifyRespDTO compensateTerminationNotify() {
        log.info("收到解约通知补偿请求");
        return terminationInternalService.compensateTerminationNotify();
    }

    @PostMapping("/compensateChannelSync")
    public CompensateNotifyRespDTO compensateChannelSync() {
        log.info("收到账户支付通道清理补偿请求");
        return terminationInternalService.compensateChannelSync();
    }

    @PostMapping("/checkFailedOrders")
    public CheckFailedOrdersRespDTO checkFailedOrders(@RequestBody CheckFailedOrdersReqDTO request) {
        log.info("收到查询扣费失败订单请求, request={}", request);
        return terminationInternalService.checkFailedOrders(request);
    }

    @PostMapping("/execute")
    public BaseRespDTO executeTermination(@RequestBody ExecuteTerminationReqDTO request) {
        log.info("收到执行解约请求, request={}", request);
        return terminationInternalService.executeTermination(request);
    }

    @PostMapping("/notifyFailed")
    public BaseRespDTO notifyTerminationFailed(@RequestBody NotifyTerminationFailedReqDTO request) {
        log.info("收到通知解约失败请求, request={}", request);
        return terminationInternalService.notifyTerminationFailed(request);
    }
}
