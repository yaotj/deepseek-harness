package com.chinasofti.huateng.facepay.controller.internal;

import com.chinasofti.huateng.common.response.CommonResult;
import com.chinasofti.huateng.facepay.service.F2fBatchRefundService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 每日批量退款入口，由 web-admin 的 Quartz 任务触发，不对外暴露。
 *
 * <p>三条业务各一个端点、各一条 {@code sys_job}，互不影响：某一类退款出问题时只停那一条，
 * NEVER 合成一个端点 —— 甲方给的执行时间本来就不同（购票 / 充值 20 点，非现金 9、15、21 点）。
 *
 * <p>每个端点各有自己的 {@link AtomicBoolean}：上一轮还没跑完时本轮直接返 {@code 9998} 拒绝，
 * 避免同一批订单被并发发起两次退款（幂等由 {@code UK_F2F_REFUND_IDEM} 兜底，但没必要靠它）。
 */
@RestController
@RequestMapping("/internal/f2f/batch-refund")
public class BatchRefundInternalController {

    private static final String CODE_SUCCESS = "0000";

    /** 上一轮仍在执行，属限流、不是失败。 */
    private static final String CODE_BUSY = "9998";

    private static final Logger log = LoggerFactory.getLogger(BatchRefundInternalController.class);

    private final F2fBatchRefundService batchRefundService;

    private final AtomicBoolean singleTicketRunning = new AtomicBoolean(false);

    private final AtomicBoolean topupRunning = new AtomicBoolean(false);

    private final AtomicBoolean noCashRunning = new AtomicBoolean(false);

    public BatchRefundInternalController(F2fBatchRefundService batchRefundService) {
        this.batchRefundService = batchRefundService;
    }

    /** 需求 1：单程票购票未取票批量退款。 */
    @PostMapping("/single-ticket")
    public CommonResult refundSingleTicket() {
        return run("单程票退票", F2fBatchRefundService.BIZ_SINGLE_TICKET, singleTicketRunning);
    }

    /** 需求 2：TVM 充值未到账批量退款。 */
    @PostMapping("/topup")
    public CommonResult refundTopup() {
        return run("TVM充值退款", F2fBatchRefundService.BIZ_TOPUP, topupRunning);
    }

    /** 需求 3：BOM 非现金收款未履约批量退款。 */
    @PostMapping("/no-cash")
    public CommonResult refundNoCash() {
        return run("非现金收款退款", F2fBatchRefundService.BIZ_NO_CASH, noCashRunning);
    }

    private CommonResult run(String taskName, List<String> bizTypes, AtomicBoolean guard) {
        if (!guard.compareAndSet(false, true)) {
            log.warn("上一轮批量退款仍在执行，本轮跳过, task={}", taskName);
            return result(CODE_BUSY, "上一轮" + taskName + "仍在执行");
        }
        try {
            F2fBatchRefundService.BatchRefundResult outcome = batchRefundService.refundBatch(taskName, bizTypes);
            return result(CODE_SUCCESS, taskName + "完成: 候选=" + outcome.scanned()
                    + ", 已发起=" + outcome.submitted()
                    + ", 跳过=" + outcome.skipped()
                    + ", 异常=" + outcome.failed());
        } finally {
            guard.set(false);
        }
    }

    private static CommonResult result(String retCode, String retMsg) {
        CommonResult response = new CommonResult();
        response.setRetCode(retCode);
        response.setRetMsg(retMsg);
        return response;
    }
}
