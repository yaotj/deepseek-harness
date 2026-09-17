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

/** ITP 解约内部接口控制器。 */
@RestController
@RequestMapping("/internal/termination")
public class TerminationInternalController {

    private static final Logger log = LoggerFactory.getLogger(TerminationInternalController.class);

    private final TerminationInternalService terminationInternalService;

    /** 协作者一律构造注入（2026-09-16，ADR-D96）：字段 {@code final} ⇒ 对象一建成即完备。 */
    public TerminationInternalController(TerminationInternalService terminationInternalService) {
        this.terminationInternalService = terminationInternalService;
    }

    /** 批量处理待解约申请：内部扫一批 PENDING + SCANNING 记录逐条处理。 */
    @PostMapping("/process")
    public ProcessTerminationRespDTO processTermination(
            @RequestBody(required = false) ProcessTerminationReqDTO request) {
        log.info("收到解约申请批处理请求, request={}", request);
        return terminationInternalService.processTermination(request);
    }

    /** 解约通知补偿：无入参，内部扫一批 NOTIFY_STATUS=FAILED 且未超重试上限的解约申请重发通知。 */
    @PostMapping("/compensateNotify")
    public CompensateNotifyRespDTO compensateTerminationNotify() {
        log.info("收到解约通知补偿请求");
        return terminationInternalService.compensateTerminationNotify();
    }

    /** 账户支付通道清理补偿：无入参，内部扫一批已解约成功但通道尚未清理的记录重新调账户域删通道。 */
    @PostMapping("/compensateChannelSync")
    public CompensateNotifyRespDTO compensateChannelSync() {
        log.info("收到账户支付通道清理补偿请求");
        return terminationInternalService.compensateChannelSync();
    }

    /** 查询用户是否存在扣费失败订单。 */
    @PostMapping("/checkFailedOrders")
    public CheckFailedOrdersRespDTO checkFailedOrders(@RequestBody CheckFailedOrdersReqDTO request) {
        log.info("收到查询扣费失败订单请求, request={}", request);
        return terminationInternalService.checkFailedOrders(request);
    }

    /** 执行支付平台解约。 */
    @PostMapping("/execute")
    public BaseRespDTO executeTermination(@RequestBody ExecuteTerminationReqDTO request) {
        log.info("收到执行解约请求, request={}", request);
        return terminationInternalService.executeTermination(request);
    }

    /** 通知APP解约失败。 */
    @PostMapping("/notifyFailed")
    public BaseRespDTO notifyTerminationFailed(@RequestBody NotifyTerminationFailedReqDTO request) {
        log.info("收到通知解约失败请求, request={}", request);
        return terminationInternalService.notifyTerminationFailed(request);
    }
}
