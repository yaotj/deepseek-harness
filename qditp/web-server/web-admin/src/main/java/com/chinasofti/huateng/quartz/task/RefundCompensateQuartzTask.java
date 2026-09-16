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
 * 退款补偿任务：驱动 pay-sign-server 的两个退款相关内部端点。
 *
 * <p>两个入口对应两件**不同的事**，pay-sign 侧也是两个独立端点，**不可互相替代**，
 * 前台需要各建一条 sys_job：
 * <ul>
 *   <li>{@code refundCompensateQuartzTask.compensateRefundQuery()}
 *       → {@code POST /internal/payment/compensateRefundQuery}，扫 {@code PAY_REFUND_DETAIL} 里停在
 *       {@code PROCESSING} 的退款单，<b>会出网</b>调支付中心 §3.2 refundQuery 回查并 CAS 收口。
 *       它是 {@code requestRefund} 摘掉 {@code @Transactional} 后的配套补偿，
 *       <b>停掉等于让那批单子永久悬挂</b>。</li>
 *   <li>{@code refundCompensateQuartzTask.compensateRefundSummary()}
 *       → {@code POST /internal/payment/compensateRefundSummary}，<b>不出网</b>，
 *       只重算 {@code PAY_TXN_DETAIL} 的 {@code REFUND_AMOUNT} / {@code REFUND_STATUS}。</li>
 * </ul>
 *
 * <p>本类 MUST 位于 Quartz 调用白名单包 com.chinasofti.huateng.quartz.task 下
 * （{@code Constants.JOB_WHITELIST_STR}），否则前台配了 invokeTarget 也调不到。</p>
 *
 * <p><b>compensateRefundQuery 的调度间隔 MUST 大于下游的 staleMinutes（pay-sign 侧
 * {@code REFUND_QUERY_STALE_MINUTES}=5 分钟），建议 cron {@code 0 0/10 * * * ?}。</b>
 * 下游只捞「距上次发起退款已超 5 分钟」的单子，间隔更短时同一批单子会在还没进入可回查窗口时被反复扫到，
 * 白跑一轮还重复出网。**NEVER 在单次调度内循环排空**——理由与
 * {@link NotifyCompensateQuartzTask} 相同：排空只能靠 cron 周期。</p>
 *
 * <p><b>compensateRefundSummary 的 skipped 长期非 0 是预期，不是故障。</b>其中含「明细已 SUCCESS 但
 * {@code PAY_TXN_DETAIL} 里没有该 ORDER_NO」这类<b>不可自愈</b>的记录（当前库里实测 6 条），
 * 每轮都会被重复计入 skipped。因此本类的汇总入口 <b>MUST NOT</b> 照抄
 * {@link NotifyCompensateQuartzTask} 里「{@code skipped > 0} 就 {@code log.error}」的写法——
 * 那会让调度日志每轮报一条 ERROR、把真实故障淹掉；这里改成 {@code log.warn} 并在消息里点明
 * 「含不可自愈记录、需人工核对、不代表本轮失败」。<b>NEVER 抄错这一条。</b></p>
 *
 * <p>失败判定与样板一致：响应为 null 或 {@code resultCode != "0000"} MUST 抛异常，
 * 因为 Quartz 只以异常判定失败，静默返回会把失败记成成功。</p>
 */
@Component("refundCompensateQuartzTask")
public class RefundCompensateQuartzTask {

    private static final Logger log = LoggerFactory.getLogger(RefundCompensateQuartzTask.class);

    private static final String SUCCESS_CODE = "0000";

    private final PaySignClient paySignClient;

    public RefundCompensateQuartzTask(PaySignClient paySignClient) {
        this.paySignClient = paySignClient;
    }

    /**
     * 退款回查补偿。前台调用目标：refundCompensateQuartzTask.compensateRefundQuery()。
     *
     * <p>本入口<b>会出网</b>调支付中心，cron MUST 大于 5 分钟，建议 {@code 0 0/10 * * * ?}。</p>
     */
    public void compensateRefundQuery() {
        invoke("退款回查补偿", paySignClient::compensateRefundQuery, false);
    }

    /**
     * 退款汇总跨表对账补偿。前台调用目标：refundCompensateQuartzTask.compensateRefundSummary()。
     *
     * <p>本入口<b>不出网</b>，只重算本地汇总，属日终级别，建议 {@code 0 15 * * * ?}（每小时第 15 分）。
     * skipped 非 0 走 WARN 而非 ERROR，理由见类注释。</p>
     */
    public void compensateRefundSummary() {
        invoke("退款汇总跨表对账补偿", paySignClient::compensateRefundSummary, true);
    }

    /**
     * 统一的 traceId 包装：Quartz 进来时 traceId 已由 AbstractQuartzJob.before() 放入 MDC，
     * 并会被 after() 写进 sys_job_log.job_message，{@link QuartzTraceUtils#runWithTrace} 直接复用它，
     * MUST NOT 另生成一个——否则前台调度日志里的 traceId 与实际发给 pay-sign 的对不上。
     */
    private void invoke(String bizName,
                        Function<Map<String, String>, CompensateNotifyRespDTO> action,
                        boolean skippedExpected) {
        QuartzTraceUtils.runWithTrace(traceId -> invokeOnce(bizName, action, skippedExpected, traceId));
    }

    /**
     * 只调一次下游，并显式判定结果。失败 MUST 抛异常：Quartz 只以异常判定失败。
     *
     * <p>NEVER 用 submitted 判断某一笔退款的结果——它只代表本轮提交了几笔，
     * 单条结果看 {@code PAY_REFUND_DETAIL.REFUND_STATUS}。</p>
     */
    private void invokeOnce(String bizName,
                            Function<Map<String, String>, CompensateNotifyRespDTO> action,
                            boolean skippedExpected,
                            String traceId) {
        CompensateNotifyRespDTO response = action.apply(QuartzTraceUtils.traceHeaders(traceId));
        if (response == null) {
            throw new IllegalStateException(bizName + "接口未返回响应");
        }
        if (!SUCCESS_CODE.equals(response.getResultCode())) {
            throw new IllegalStateException(bizName + "失败, resultCode=" + response.getResultCode()
                    + ", resultMsg=" + response.getResultMsg());
        }
        if (response.getSkipped() > 0) {
            if (skippedExpected) {
                // 汇总对账的 skipped 里混着「明细已 SUCCESS 但原支付订单不存在」这类不可自愈记录，
                // 长期非 0 属预期。MUST 用 warn：写 error 会让每轮调度都报错、把真实故障淹掉。
                log.warn("{}存在未处理记录（其中含不可自愈记录，需人工核对，不代表本轮失败）, scanned={}, submitted={}, skipped={}",
                        bizName, response.getScanned(), response.getSubmitted(), response.getSkipped());
            } else {
                log.error("{}存在提交失败记录, scanned={}, submitted={}, skipped={}",
                        bizName, response.getScanned(), response.getSubmitted(), response.getSkipped());
            }
        }
        log.info("{}完成, scanned={}, submitted={}, skipped={}",
                bizName, response.getScanned(), response.getSubmitted(), response.getSkipped());
    }
}
