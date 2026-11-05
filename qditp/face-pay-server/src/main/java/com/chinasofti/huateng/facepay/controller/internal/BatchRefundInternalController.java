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

@RestController
@RequestMapping("/internal/f2f/batch-refund")
public class BatchRefundInternalController {

    private static final String CODE_SUCCESS = "0000";

    private static final String CODE_BUSY = "9998";

    private static final Logger log = LoggerFactory.getLogger(BatchRefundInternalController.class);

    private final F2fBatchRefundService batchRefundService;

    private final AtomicBoolean singleTicketRunning = new AtomicBoolean(false);

    private final AtomicBoolean topupRunning = new AtomicBoolean(false);

    private final AtomicBoolean noCashRunning = new AtomicBoolean(false);

    public BatchRefundInternalController(F2fBatchRefundService batchRefundService) {
        this.batchRefundService = batchRefundService;
    }

    @PostMapping("/single-ticket")
    public CommonResult refundSingleTicket() {
        log.info("app购买单程票购票未取票批量退款 定时任务开始执行");
        return run("单程票退票", F2fBatchRefundService.BIZ_SINGLE_TICKET, singleTicketRunning);
    }

    @PostMapping("/topup")
    public CommonResult refundTopup() {
        log.info("TVM 充值未到账批量退款 定时任务开始执行");
        return run("TVM充值退款", F2fBatchRefundService.BIZ_TOPUP, topupRunning);
    }

    @PostMapping("/no-cash")
    public CommonResult refundNoCash() {
        log.info("BOM 非现金收款未履约批量退款 定时任务开始执行");
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
