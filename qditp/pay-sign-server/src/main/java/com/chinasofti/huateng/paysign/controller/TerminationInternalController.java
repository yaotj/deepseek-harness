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

/**
 * ITP 解约内部接口控制器。
 */
@RestController
@RequestMapping("/internal/termination")
public class TerminationInternalController {

    private static final Logger log = LoggerFactory.getLogger(TerminationInternalController.class);

    private final TerminationInternalService terminationInternalService;

    /**
     * 协作者一律构造注入（2026-09-16，ADR-D96）：字段 {@code final} ⇒ 对象一建成即完备，
     * 且夹具漏注 / 多注一个协作者会**编译失败**，而不是运行时才报 {@code Could not find field}。
     * <b>NEVER 退回 {@code @Autowired} 字段注入。</b>
     */
    public TerminationInternalController(TerminationInternalService terminationInternalService) {
        this.terminationInternalService = terminationInternalService;
    }

    /**
     * 批量处理待解约申请：内部扫一批 PENDING + SCANNING 记录逐条处理。
     * PENDING 先查该用户是否还有未结清扣费订单，有则置失败并通知 APP，无则发起支付平台解约；
     * SCANNING 主动查支付平台协议状态收口。调用方可反复调用直到 scanned 为 0。
     *
     * <p>入参可省略（不传 body 即历史行为，不按申请时间过滤）。传
     * {@code referenceTime} / {@code delayDays} 时只处理
     * {@code REQUEST_TIME <= referenceTime - delayDays} 的记录，用于「解约申请满 N 天才确认」
     * 的业务口径；delayDays 缺省取配置 {@code termination.confirm-delay-days}（默认 4）。</p>
     */
    @PostMapping("/process")
    public ProcessTerminationRespDTO processTermination(
            @RequestBody(required = false) ProcessTerminationReqDTO request) {
        log.info("收到解约申请批处理请求, request={}", request);
        return terminationInternalService.processTermination(request);
    }

    /**
     * 解约通知补偿：无入参，内部扫一批 NOTIFY_STATUS=FAILED 且未超重试上限的解约申请重发通知。
     * 由外部定时任务调度，调用方可反复调用直到 scanned 为 0。
     *
     * <p>重发是异步的，通知是否成功以 APP_TERMINATION_REQUEST 的 NOTIFY_STATUS / NOTIFY_RESULT 为准，
     * 不要用返回的 submitted 判断结果。</p>
     */
    @PostMapping("/compensateNotify")
    public CompensateNotifyRespDTO compensateTerminationNotify() {
        log.info("收到解约通知补偿请求");
        return terminationInternalService.compensateTerminationNotify();
    }

    /**
     * 账户支付通道清理补偿：无入参，内部扫一批已解约成功但通道尚未清理的记录重新调账户域删通道。
     * 由 web-admin Quartz 调度，调用方可反复调用直到 scanned 为 0。
     *
     * <p>扫的是 TERMINATION_STATUS='SUCCESS' 且 CHANNEL_SYNC_STATUS 为 FAILED / 超时 PENDING 的记录；
     * CHANNEL_SYNC_STATUS 为 NULL（历史数据）与 MANUAL（账户域已明确拒绝、需人工）刻意不捞。
     * NEVER 与 /compensateNotify 合并成一个端点：两者的重试上限、失败语义与人工介入口径都不同。</p>
     *
     * <p>清理结果以 APP_TERMINATION_REQUEST 的 CHANNEL_SYNC_STATUS / CHANNEL_SYNC_RESULT 为准，
     * 不要用返回的 submitted 判断单条结果。</p>
     */
    @PostMapping("/compensateChannelSync")
    public CompensateNotifyRespDTO compensateChannelSync() {
        log.info("收到账户支付通道清理补偿请求");
        return terminationInternalService.compensateChannelSync();
    }

    /**
     * 查询用户是否存在扣费失败订单。
     */
    @PostMapping("/checkFailedOrders")
    public CheckFailedOrdersRespDTO checkFailedOrders(@RequestBody CheckFailedOrdersReqDTO request) {
        log.info("收到查询扣费失败订单请求, request={}", request);
        return terminationInternalService.checkFailedOrders(request);
    }

    /**
     * 执行支付平台解约。
     *
     * <p>库中没有该签约流水的解约申请时，先按签约记录补建一条 PENDING 申请再执行；
     * 连签约记录也查不到则返回「用户未签约」，不凭空造申请。</p>
     */
    @PostMapping("/execute")
    public BaseRespDTO executeTermination(@RequestBody ExecuteTerminationReqDTO request) {
        log.info("收到执行解约请求, request={}", request);
        return terminationInternalService.executeTermination(request);
    }

    /**
     * 通知APP解约失败。
     */
    @PostMapping("/notifyFailed")
    public BaseRespDTO notifyTerminationFailed(@RequestBody NotifyTerminationFailedReqDTO request) {
        log.info("收到通知解约失败请求, request={}", request);
        return terminationInternalService.notifyTerminationFailed(request);
    }
}
