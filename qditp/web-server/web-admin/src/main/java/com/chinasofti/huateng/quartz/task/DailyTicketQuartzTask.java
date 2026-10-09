package com.chinasofti.huateng.quartz.task;

import com.chinasofti.huateng.model.app.dailyticket.DailyTicketBaseResult;
import com.chinasofti.huateng.quartz.util.QuartzTraceUtils;
import com.chinasofti.huateng.rpc.dailyticket.DailyTicketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 日票相关补偿任务。
 *
 * <p>Quartz 调度入口在 web-admin，具体补偿逻辑由 daily-ticket-server
 * 的内部接口执行。这样 daily-ticket 不需要引入 {@code @Scheduled}。</p>
 */
@Component("dailyTicketQuartzTask")
public class DailyTicketQuartzTask {

    private static final Logger log = LoggerFactory.getLogger(DailyTicketQuartzTask.class);
    private static final String SUCCESS_CODE = "0000";

    /** 上一轮仍在执行，属限流、不是失败：只有过期收敛那条端点会返它。 */
    private static final String CODE_BUSY = "9998";

    private final DailyTicketClient dailyTicketClient;

    public DailyTicketQuartzTask(DailyTicketClient dailyTicketClient) {
        this.dailyTicketClient = dailyTicketClient;
    }

    /**
     * 日票/旅游票支付结果通知 APP（IF8B-05）补偿。
     *
     * <p>前台 Quartz 调用目标：
     * {@code dailyTicketQuartzTask.compensatePayNotify()}。</p>
     */
    public void compensatePayNotify() {
        QuartzTraceUtils.runWithTrace(traceId -> {
            DailyTicketBaseResult response =
                    dailyTicketClient.compensatePayNotify(QuartzTraceUtils.traceHeaders(traceId));
            assertSuccess("日票支付结果通知补偿", response);
        });
    }

    /**
     * 日票激活后通知 ACC 发售补偿。
     *
     * <p>前台 Quartz 调用目标：
     * {@code dailyTicketQuartzTask.compensateAccActiveNotify()}。</p>
     */
    public void compensateAccActiveNotify() {
        QuartzTraceUtils.runWithTrace(traceId -> {
            DailyTicketBaseResult response =
                    dailyTicketClient.compensateAccActiveNotify(QuartzTraceUtils.traceHeaders(traceId));
            assertSuccess("日票ACC发售通知补偿", response);
        });
    }

    /**
     * 日票有效期过期收敛（{@code sys_job} 350）。前台调用目标：dailyTicketQuartzTask.convergeExpiredTickets()。
     *
     * <p>下游端点自带 {@code AtomicBoolean} 限流，返 {@code 9998} 时**不当失败**
     * （否则前台调度日志每轮都记红），因此这里不能复用 {@link #assertSuccess}。
     */
    public void convergeExpiredTickets() {
        QuartzTraceUtils.runWithTrace(traceId -> {
            DailyTicketBaseResult response =
                    dailyTicketClient.convergeExpiredTickets(QuartzTraceUtils.traceHeaders(traceId));
            if (response == null) {
                throw new IllegalStateException("日票过期收敛接口未返回响应");
            }
            if (CODE_BUSY.equals(response.getRetCode())) {
                log.warn("日票过期收敛被限流，上一轮仍在执行, retMsg={}", response.getRetMsg());
                return;
            }
            if (!SUCCESS_CODE.equals(response.getRetCode())) {
                throw new IllegalStateException("日票过期收敛失败, retCode="
                        + response.getRetCode() + ", retMsg=" + response.getRetMsg());
            }
            log.info("日票过期收敛完成, retMsg={}", response.getRetMsg());
        });
    }

    private void assertSuccess(String bizName, DailyTicketBaseResult response) {
        if (response == null) {
            throw new IllegalStateException(bizName + "接口未返回响应");
        }
        if (!SUCCESS_CODE.equals(response.getRetCode())) {
            throw new IllegalStateException(bizName + "失败, retCode="
                    + response.getRetCode() + ", retMsg=" + response.getRetMsg());
        }
        log.info("{}完成, data={}, retMsg={}", bizName, response.getData(), response.getRetMsg());
    }
}
