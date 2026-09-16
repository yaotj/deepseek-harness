package com.chinasofti.huateng.quartz.task;

import com.chinasofti.huateng.model.app.BlacklistReleaseCandidateDTO;
import com.chinasofti.huateng.model.app.BlacklistReleaseInspectRespDTO;
import com.chinasofti.huateng.quartz.util.QuartzTraceUtils;
import com.chinasofti.huateng.rpc.blacklist.BlacklistClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 黑名单「可解除性」盘点任务。
 *
 * <p>前台调用目标：{@code blacklistReleaseInspectQuartzTask.inspect()}，建议 cron
 * {@code 0 0 10,16 * * ?}（每天 10 点、16 点各一次）。</p>
 *
 * <p><b>本任务只读，NEVER 解除任何黑名单。</b>它把每条黑名单记录的欠费事实（闸机出站扣费 +
 * 支付宝出行两个源）查清楚后输出到日志，由人工据 REASON 判断该不该解除。
 * 之所以不直接删：{@code BLACKLIST} 只有 5 列、没有拉黑类型字段，REASON 是四个来源混写的自由文本，
 * 生产实测 35 条 ADD 里 22 条是「用户挂失补卡」——与欠费无关，按「欠费结清」删掉等于让挂失旧卡恢复过闸。
 * 等拉黑类型落库、且支付宝侧把「幂等拒答误判成扣款失败」的缺陷修掉之后，才能在此基础上加自动删除。</p>
 *
 * <p>与 {@link NotifyCompensateQuartzTask} 一样**不在单次调度内循环**：本任务是只读盘点，
 * 不改变任何状态，循环调用只会重复输出同一批，没有意义。批量范围由 blacklist-server 侧
 * {@code blacklist.inspect.batch-size} 控制。</p>
 *
 * <p>任务必须位于 Quartz 调用白名单包 com.chinasofti.huateng.quartz.task 下
 * （{@code Constants.JOB_WHITELIST_STR}）。</p>
 */
@Component("blacklistReleaseInspectQuartzTask")
public class BlacklistReleaseInspectQuartzTask {

    private static final Logger log = LoggerFactory.getLogger(BlacklistReleaseInspectQuartzTask.class);

    private static final String SUCCESS_CODE = "0000";

    private final BlacklistClient blacklistClient;

    public BlacklistReleaseInspectQuartzTask(BlacklistClient blacklistClient) {
        this.blacklistClient = blacklistClient;
    }

    /**
     * 盘点黑名单可解除性。前台调用目标：blacklistReleaseInspectQuartzTask.inspect()。
     *
     * <p>traceId 已由 AbstractQuartzJob.before() 放入 MDC 并会被 after() 写进
     * sys_job_log.job_message，{@link QuartzTraceUtils#runWithTrace} 直接复用它，MUST NOT 另生成一个——
     * 否则调度日志里的 traceId 与实际发给下游的对不上。</p>
     */
    public void inspect() {
        QuartzTraceUtils.runWithTrace(this::inspectOnce);
    }

    /**
     * 只调一次下游并显式判定结果。失败 MUST 抛异常：Quartz 只以异常判定失败，
     * 静默返回会让调度日志记成成功。
     */
    private void inspectOnce(String traceId) {
        BlacklistReleaseInspectRespDTO response = blacklistClient.inspectReleasable(
                QuartzTraceUtils.traceHeaders(traceId));
        if (response == null) {
            throw new IllegalStateException("黑名单可解除性盘点接口未返回响应");
        }
        if (!SUCCESS_CODE.equals(response.getResultCode())) {
            throw new IllegalStateException("黑名单可解除性盘点失败, resultCode=" + response.getResultCode()
                    + ", resultMsg=" + response.getResultMsg());
        }

        // unknown 是「至少一个欠费源没查成功」，事实不明，本次盘点结论对这些记录不可用，需人工看一眼。
        if (response.getUnknown() > 0) {
            log.error("黑名单可解除性盘点存在查询失败记录, MUST 人工核对, scanned={}, unknown={}",
                    response.getScanned(), response.getUnknown());
        }
        log.info("黑名单可解除性盘点完成, scanned={}, settled={}, unsettled={}, unknown={}",
                response.getScanned(), response.getSettled(), response.getUnsettled(), response.getUnknown());
        logSettledDetails(response.getDetails());
    }

    /**
     * 把「欠费已结清」的记录逐条打出来，供人工判断是否该解除。
     *
     * <p>MUST 带上 reason 原文：SETTLED 只代表钱结清，挂失补卡类同样会是 SETTLED，
     * **NEVER** 把这份清单当成「可以删的名单」直接执行。</p>
     */
    private void logSettledDetails(List<BlacklistReleaseCandidateDTO> details) {
        if (details == null || details.isEmpty()) {
            return;
        }
        for (BlacklistReleaseCandidateDTO detail : details) {
            if (!"SETTLED".equals(detail.getSettleStatus())) {
                continue;
            }
            log.info("黑名单欠费已结清待人工判断, cardId={}, thirdUserId={}, createTime={}, reason={}",
                    detail.getCardId(), detail.getThirdUserId(), detail.getCreateTime(), detail.getReason());
        }
    }
}
