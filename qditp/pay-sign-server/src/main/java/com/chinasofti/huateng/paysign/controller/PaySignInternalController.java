package com.chinasofti.huateng.paysign.controller;

import com.chinasofti.huateng.model.paysign.ResendSignNotifyReqDTO;
import com.chinasofti.huateng.model.paysign.ResendSignNotifyRespDTO;
import com.chinasofti.huateng.model.paysign.CompensateNotifyRespDTO;
import com.chinasofti.huateng.paysign.service.AppNotifyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ITP 签约内部接口控制器。
 */
@RestController
@RequestMapping("/internal/paySign")
public class PaySignInternalController {

    private static final Logger log = LoggerFactory.getLogger(PaySignInternalController.class);

    private final AppNotifyService appNotifyService;

    /**
     * 协作者一律构造注入（2026-09-16，ADR-D96）：字段 {@code final} ⇒ 对象一建成即完备，
     * 且夹具漏注 / 多注一个协作者会**编译失败**，而不是运行时才报 {@code Could not find field}。
     * <b>NEVER 退回 {@code @Autowired} 字段注入。</b>
     */
    public PaySignInternalController(AppNotifyService appNotifyService) {
        this.appNotifyService = appNotifyService;
    }

    /**
     * 签约结果通知补偿：无入参，内部扫一批 APP_PAY_SIGN_REQUEST 里 OPERATION_TYPE=RECEIVE_SIGN_RESULT
     * 且通知未收口（FAILED 或滞留 PENDING）、未超重试上限的流水重发通知。由外部定时任务调度，
     * 调用方可反复调用直到 scanned 为 0。
     *
     * <p><b>只负责签约结果通知。</b>解约结果通知的补偿在 /internal/termination/compensateNotify，
     * 队列是 APP_TERMINATION_REQUEST。两者各自管一类通知，NEVER 让本接口再去扫
     * RECEIVE_TERMINATION_RESULT 流水——那会让同一条解约通知被两个队列各自重发一次。</p>
     *
     * <p>重发是异步的，通知是否成功以 APP_PAY_SIGN_REQUEST 的 NOTIFY_STATUS / NOTIFY_RESULT 为准，
     * 不要用返回的 submitted 判断结果。</p>
     */
    @PostMapping("/compensateNotify")
    public CompensateNotifyRespDTO compensateSignNotify() {
        log.info("收到签约流水通知补偿请求");
        return appNotifyService.compensateSignNotify();
    }

    /**
     * 单条签约结果通知重发：只处理入参给定的 requestSignSeq，**不扫表、不递增 NOTIFY_RETRY_COUNT**。
     *
     * <p>用途是联调与运维人工重放某一笔签约成功通知。**NEVER 为此改用
     * /internal/paySign/compensateNotify**——批量补偿会把库里所有符合扫描条件的历史流水一起推给 APP，
     * 而 APP 侧实测没有按 requestSignSeq 幂等，会被当成新签约处理，污染对端状态且我方无法回滚。</p>
     *
     * <p>前置校验是白名单：签约记录存在且 SIGN_STATUS=SIGNED、且已有 RECEIVE_SIGN_RESULT 流水，
     * 否则拒绝。投递是同步的，返回体的 notified 即真实结果，同时回写该流水的
     * NOTIFY_STATUS / NOTIFY_TIME / NOTIFY_RESULT。</p>
     *
     * <p>⚠️ 与 /internal/** 下其余接口一样，本接口**没有鉴权**（模块无 spring-security、无全局拦截器，
     * 属已登记的 P0）。它能按流水号给 APP 触发一次通知，因此上线前 MUST 确认该端口不对外暴露；
     * 补鉴权时 MUST 与 /internal/termination/** 一起做，NEVER 在这里自造签名逻辑。</p>
     */
    @PostMapping("/resendNotify")
    public ResendSignNotifyRespDTO resendSignNotify(@RequestBody ResendSignNotifyReqDTO request) {
        String requestSignSeq = request == null ? null : request.getRequestSignSeq();
        log.info("收到单条签约结果通知重发请求, requestSignSeq={}", requestSignSeq);
        return appNotifyService.resendSignNotify(requestSignSeq);
    }
}
