package com.chinasofti.huateng.quartz.task;

import com.chinasofti.huateng.model.app.BlacklistAutoReleaseRespDTO;
import com.chinasofti.huateng.quartz.util.QuartzTraceUtils;
import com.chinasofti.huateng.rpc.blacklist.BlacklistClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 自动解除黑名单任务（{@code sys_job} 230，每天 10 点与 16 点各一次）。
 *
 * <p>触发 blacklist-server 的 {@code POST /internal/blacklist/auto-release}：扫 {@code BLACK_CAUSE='01'}
 * （欠费类）且生效中的行，按 {@code CHANNEL_CODE} 查对应欠费源（01 地铁APP 查闸机出站扣费、
 * 02 支付宝查支付宝出行、99 未知渠道跳过），欠费已结清即走两阶段解除。
 *
 * <p>这是本项目<b>第一个会自动解除黑名单的环节</b>：此前 {@code sys_job} 280「黑名单可解除性盘点」
 * 只读、日志写的是「待人工判断」，320 / 325 只推渠道通知、不改黑名单本体。
 * <b>NEVER 把本任务与 280 合并</b> —— 只读盘点要能把全部加黑原因都报出来给人看，
 * 自动解除只能覆盖「原因可被机器证明已失效」的欠费类。
 * （2026-09-21 改号：105→280、125→320、126→325。）
 *
 * <p>该模块<b>刻意没有 {@code @Scheduled}</b>，调度统一在 web-admin。
 * <b>NEVER 为了省事回头在 blacklist-server 里加 {@code @Scheduled}。</b>
 */
@Component("blacklistAutoReleaseQuartzTask")
public class BlacklistAutoReleaseQuartzTask {

    private static final Logger log = LoggerFactory.getLogger(BlacklistAutoReleaseQuartzTask.class);

    private final BlacklistClient blacklistClient;

    public BlacklistAutoReleaseQuartzTask(BlacklistClient blacklistClient) {
        this.blacklistClient = blacklistClient;
    }

    /** 前台调用目标：blacklistAutoReleaseQuartzTask.autoRelease()。 */
    public void autoRelease() {
        QuartzTraceUtils.runWithTrace(traceId -> {
            BlacklistAutoReleaseRespDTO result = blacklistClient.autoRelease(
                    null, QuartzTraceUtils.traceHeaders(traceId));
            logResult(result);
        });
    }

    /**
     * 结果判定。
     *
     * <p>响应为 {@code null} MUST 抛异常：静默返回会让调度日志记成成功，于是「自动解除根本没跑通」看着一切正常。
     *
     * <p>{@code failed}（解除动作自身失败）与 {@code unknown}（欠费查询不通、事实不明）
     * <b>只打 ERROR、NEVER 抛异常</b>：这两类下一轮会重入，抛出去只会把 {@code SYS_JOB_LOG} 刷成一片红。
     * {@code skipped}（渠道 99、定位不到欠费源）打 WARN —— 它不会自愈，长期不为 0 说明加黑入口没送
     * {@code channelCode}，MUST 从加黑侧治，<b>NEVER 在解除侧猜一个渠道</b>。
     */
    private void logResult(BlacklistAutoReleaseRespDTO result) {
        if (result == null) {
            throw new IllegalStateException("黑名单自动解除接口未返回响应");
        }
        if (result.getFailed() > 0 || result.getUnknown() > 0) {
            log.error("黑名单自动解除存在未闭合记录, MUST 人工核对, scanned={}, released={}, unsettled={},"
                            + " unknown={}, skipped={}, failed={}",
                    result.getScanned(), result.getReleased(), result.getUnsettled(),
                    result.getUnknown(), result.getSkipped(), result.getFailed());
            return;
        }
        if (result.getSkipped() > 0) {
            log.warn("黑名单自动解除存在渠道未知的记录, 这类行不会自愈、MUST 从加黑入口补 channelCode,"
                            + " scanned={}, released={}, unsettled={}, skipped={}",
                    result.getScanned(), result.getReleased(), result.getUnsettled(), result.getSkipped());
            return;
        }
        log.info("黑名单自动解除完成, scanned={}, released={}, unsettled={}, unknown={}, skipped={}, failed={}",
                result.getScanned(), result.getReleased(), result.getUnsettled(),
                result.getUnknown(), result.getSkipped(), result.getFailed());
    }
}
