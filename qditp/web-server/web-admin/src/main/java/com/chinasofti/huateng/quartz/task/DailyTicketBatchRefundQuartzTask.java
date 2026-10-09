package com.chinasofti.huateng.quartz.task;

import com.chinasofti.huateng.model.app.dailyticket.DailyTicketBaseResult;
import com.chinasofti.huateng.quartz.util.QuartzTraceUtils;
import com.chinasofti.huateng.rpc.dailyticket.DailyTicketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Function;

/**
 * daily-ticket-server 的 Quartz 调用桥接任务：多日票批量退款（甲方需求 16 当日 / 17 月度）。
 *
 * <p>两个方法对应两条 {@code sys_job}（245 / 250，2026-09-21 由 135 / 136 改号），触发时机不同（每天 20 点 / 每月最后一天 20 点），
 * 停用其中一条不影响另一条。
 *
 * <p>下游端点自带 {@code AtomicBoolean} 限流，返 {@code 9998} 时**不当失败**（否则前台每轮都记红）。
 */
@Component("dailyTicketBatchRefundQuartzTask")
public class DailyTicketBatchRefundQuartzTask {

    /** 上一轮仍在执行，属限流、不是失败。 */
    private static final String CODE_BUSY = "9998";

    private static final String SUCCESS_CODE = "0000";

    private static final Logger log = LoggerFactory.getLogger(DailyTicketBatchRefundQuartzTask.class);

    private final DailyTicketClient dailyTicketClient;

    public DailyTicketBatchRefundQuartzTask(DailyTicketClient dailyTicketClient) {
        this.dailyTicketClient = dailyTicketClient;
    }

    /** 前台调用目标填写 dailyTicketBatchRefundQuartzTask.refundDaily() 时执行。 */
    public void refundDaily() {
        QuartzTraceUtils.runWithTrace(traceId -> invoke("多日票批量退款(当日)",
                dailyTicketClient::batchRefundDaily, traceId));
    }

    /** 前台调用目标填写 dailyTicketBatchRefundQuartzTask.refundMonthly() 时执行。 */
    public void refundMonthly() {
        QuartzTraceUtils.runWithTrace(traceId -> invoke("多日票批量退款(月度)",
                dailyTicketClient::batchRefundMonthly, traceId));
    }

    private void invoke(String taskName, Function<Map<String, String>, DailyTicketBaseResult> call, String traceId) {
        DailyTicketBaseResult response = call.apply(QuartzTraceUtils.traceHeaders(traceId));
        if (response == null) {
            throw new IllegalStateException("daily-ticket-server " + taskName + "接口未返回响应");
        }
        if (CODE_BUSY.equals(response.getRetCode())) {
            log.warn("daily-ticket-server {}被限流，上一轮仍在执行, retMsg={}", taskName, response.getRetMsg());
            return;
        }
        if (!SUCCESS_CODE.equals(response.getRetCode())) {
            throw new IllegalStateException("daily-ticket-server " + taskName + "失败: retCode="
                    + response.getRetCode() + ", retMsg=" + response.getRetMsg());
        }
        log.info("daily-ticket-server {}调用成功, retMsg={}", taskName, response.getRetMsg());
    }
}
