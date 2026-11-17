package com.chinasofti.huateng.dailyticket.controller.internal;

import com.chinasofti.huateng.dailyticket.service.DailyTicketBatchRefundService;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketBaseResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.atomic.AtomicBoolean;


@RestController
@RequestMapping("/internal/daily-ticket/batch-refund")
public class BatchRefundInternalController {

    private static final String CODE_SUCCESS = "0000";

    private static final String CODE_BUSY = "9998";

    private static final Logger log = LoggerFactory.getLogger(BatchRefundInternalController.class);

    private final DailyTicketBatchRefundService batchRefundService;

    private final AtomicBoolean dailyRunning = new AtomicBoolean(false);

    private final AtomicBoolean monthlyRunning = new AtomicBoolean(false);

    public BatchRefundInternalController(DailyTicketBatchRefundService batchRefundService) {
        this.batchRefundService = batchRefundService;
    }

    @PostMapping("/daily")
    public DailyTicketBaseResult refundDaily() {
        return run("多日票批量退款(当日)", dailyRunning, true);
    }

    @PostMapping("/monthly")
    public DailyTicketBaseResult refundMonthly() {
        return run("多日票批量退款(月度)", monthlyRunning, false);
    }

    private DailyTicketBaseResult run(String taskName, AtomicBoolean guard, boolean daily) {
        if (!guard.compareAndSet(false, true)) {
            log.warn("上一轮批量退款仍在执行，本轮跳过, task={}", taskName);
            return result(CODE_BUSY, "上一轮" + taskName + "仍在执行");
        }
        try {
            DailyTicketBatchRefundService.BatchRefundResult outcome =
                    daily ? batchRefundService.refundDaily() : batchRefundService.refundMonthly();
            return result(CODE_SUCCESS, taskName + "完成: 候选=" + outcome.scanned()
                    + ", 已发起=" + outcome.submitted()
                    + ", 跳过=" + outcome.skipped()
                    + ", 异常=" + outcome.failed());
        } finally {
            guard.set(false);
        }
    }

    private static DailyTicketBaseResult result(String retCode, String retMsg) {
        DailyTicketBaseResult response = new DailyTicketBaseResult();
        response.setRetCode(retCode);
        response.setRetMsg(retMsg);
        return response;
    }
}
