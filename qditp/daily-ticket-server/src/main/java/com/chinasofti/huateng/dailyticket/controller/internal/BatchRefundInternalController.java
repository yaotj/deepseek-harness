package com.chinasofti.huateng.dailyticket.controller.internal;

import com.chinasofti.huateng.dailyticket.service.DailyTicketBatchRefundService;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketBaseResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 多日票批量退款入口（甲方需求 16 当日 / 17 月度），由 web-admin 的 Quartz 任务触发，不对外暴露。
 *
 * <p>两条业务各一个端点、各一条 {@code sys_job}，互不影响：某一条出问题时只停那一条，
 * NEVER 合成一个端点 —— 当日与月度的触发时机本来就不同（每天 20 点 / 每月最后一天 20 点）。
 *
 * <p>每个端点各有自己的 {@link AtomicBoolean}：上一轮还没跑完时本轮直接返 {@code 9998} 拒绝，
 * 避免同一批订单被并发发起两次退款（幂等由 {@code UK_DAILY_TICKET_REFUND_ORDER} 兜底，但没必要靠它）。
 * {@code 9998} 属**限流、不是失败**，上游 Quartz 任务 MUST 只记 WARN 不抛异常。
 *
 * <p>本类**无鉴权**：与 AGENTS.md §5.2「新增状态变更型接口 MUST 有鉴权」冲突，属沿用本模块
 * 现有 {@code /internal/daily-ticket/**} 端点的现状（全部裸暴露、只靠网络可达性隔离），上线前 MUST 统一补齐。
 */
@RestController
@RequestMapping("/internal/daily-ticket/batch-refund")
public class BatchRefundInternalController {

    private static final String CODE_SUCCESS = "0000";

    /** 上一轮仍在执行，属限流、不是失败。 */
    private static final String CODE_BUSY = "9998";

    private static final Logger log = LoggerFactory.getLogger(BatchRefundInternalController.class);

    private final DailyTicketBatchRefundService batchRefundService;

    private final AtomicBoolean dailyRunning = new AtomicBoolean(false);

    private final AtomicBoolean monthlyRunning = new AtomicBoolean(false);

    public BatchRefundInternalController(DailyTicketBatchRefundService batchRefundService) {
        this.batchRefundService = batchRefundService;
    }

    /** 甲方需求 16：当日批量退款，每天 20 点触发。 */
    @PostMapping("/daily")
    public DailyTicketBaseResult refundDaily() {
        return run("多日票批量退款(当日)", dailyRunning, true);
    }

    /** 甲方需求 17：月度批量退款，每月最后一天 20 点触发。 */
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
