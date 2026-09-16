package com.chinasofti.huateng.quartz.task;

import com.chinasofti.huateng.model.paysign.CompensateNotifyRespDTO;
import com.chinasofti.huateng.quartz.util.QuartzTraceUtils;
import com.chinasofti.huateng.rpc.paySign.PaySignClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Function;

/**
 * APP 通知补偿任务：把签约结果通知与解约结果通知里「没通知成功」的记录重新推给 APP。
 *
 * <p>两条链路扫的是不同的表，pay-sign 侧也是两个独立接口，**不可互相替代**，因此这里给出两个入口，
 * 前台需要各建一条 sys_job：
 * <ul>
 *   <li>{@code notifyCompensateQuartzTask.compensateSignNotify()} → APP_PAY_SIGN_REQUEST</li>
 *   <li>{@code notifyCompensateQuartzTask.compensateTerminationNotify()} → APP_TERMINATION_REQUEST</li>
 * </ul>
 *
 * <p><b>与 {@link TerminationQuartzTask} 的关键差异：本任务 NEVER 在单次调度内循环排空。</b>
 * 下游在提交重发**之前**就同步递增 NOTIFY_RETRY_COUNT 并把状态置为 FAILED，真正的通知是异步发的；
 * 若在同一次调度里再调一轮，会立刻扫到同一批（计数已 +1、异步结果还没回写），
 * 几秒内把重试预算烧光。排空只能靠 cron 周期，**调度间隔 MUST 大于下游的 PENDING 滞留阈值
 * （pay-sign 侧 {@code PENDING_STALE_MINUTES}=10 分钟）**，建议 {@code 0 0/10 * * * ?}。
 *
 * <p>任务必须位于 Quartz 调用白名单包 com.chinasofti.huateng.quartz.task 下
 * （{@code Constants.JOB_WHITELIST_STR}）。</p>
 */
@Component("notifyCompensateQuartzTask")
public class NotifyCompensateQuartzTask {

    private static final Logger log = LoggerFactory.getLogger(NotifyCompensateQuartzTask.class);

    private static final String SUCCESS_CODE = "0000";

    private final PaySignClient paySignClient;

    public NotifyCompensateQuartzTask(PaySignClient paySignClient) {
        this.paySignClient = paySignClient;
    }

    /**
     * 签约结果通知补偿。前台调用目标：notifyCompensateQuartzTask.compensateSignNotify()。
     */
    public void compensateSignNotify() {
        invoke("签约结果通知补偿", paySignClient::compensateSignNotify);
    }

    /**
     * 解约结果通知补偿。前台调用目标：notifyCompensateQuartzTask.compensateTerminationNotify()。
     */
    public void compensateTerminationNotify() {
        invoke("解约结果通知补偿", paySignClient::compensateTerminationNotify);
    }

    /**
     * 统一的 traceId 包装：Quartz 进来时 traceId 已由 AbstractQuartzJob.before() 放入 MDC，
     * 并会被 after() 写进 sys_job_log.job_message，{@link QuartzTraceUtils#runWithTrace} 直接复用它，
     * MUST NOT 另生成一个——否则前台调度日志里的 traceId 与实际发给 pay-sign 的对不上。
     */
    private void invoke(String bizName, Function<Map<String, String>, CompensateNotifyRespDTO> action) {
        QuartzTraceUtils.runWithTrace(traceId -> invokeOnce(bizName, action, traceId));
    }

    /**
     * 只调一次下游，并显式判定结果。失败 MUST 抛异常：Quartz 只以异常判定失败，
     * 静默返回会让调度日志记成成功。
     *
     * <p>NEVER 用 submitted 判断通知是否送达——它只代表「提交成功」，真实结果由下游异步回写到
     * NOTIFY_STATUS / NOTIFY_RESULT。本任务只保证「把该补的都提交出去了」。</p>
     */
    private void invokeOnce(String bizName,
                            Function<Map<String, String>, CompensateNotifyRespDTO> action,
                            String traceId) {
        CompensateNotifyRespDTO response = action.apply(QuartzTraceUtils.traceHeaders(traceId));
        if (response == null) {
            throw new IllegalStateException(bizName + "接口未返回响应");
        }
        if (!SUCCESS_CODE.equals(response.getResultCode())) {
            throw new IllegalStateException(bizName + "失败, resultCode=" + response.getResultCode()
                    + ", resultMsg=" + response.getResultMsg());
        }
        // skipped 是「提交重发时就失败」，这些记录状态未变、下次调度会再取到，不算本次失败，
        // 但持续非 0 说明通知线程池长期打满或下游异常，需要人工看一眼。
        if (response.getSkipped() > 0) {
            log.error("{}存在提交失败记录, scanned={}, submitted={}, skipped={}",
                    bizName, response.getScanned(), response.getSubmitted(), response.getSkipped());
        }
        log.info("{}完成, scanned={}, submitted={}, skipped={}",
                bizName, response.getScanned(), response.getSubmitted(), response.getSkipped());
    }
}
