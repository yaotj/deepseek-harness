package com.chinasofti.huateng.quartz.task;

import com.chinasofti.huateng.model.domain.OutboxScan;
import com.chinasofti.huateng.quartz.util.QuartzTraceUtils;
import com.chinasofti.huateng.rpc.blacklist.BlacklistClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 黑名单渠道同步（支付宝方向）通知补偿任务。
 *
 * <p>blacklist-server 里加黑与解黑的通知都走「落库状态 + 提交后出网」：afterCommit 那条是<b>快速路径</b>、
 * 进程内非持久，JVM 崩溃或对端不可达即丢，真正的兜底是主表 {@code CHANNEL_SYNC_*} 那套 outbox + 本任务重扫。
 * 在本任务建起来之前那两个端点只能手工 curl —— 也就是说<b>补偿实际上没人触发</b>。
 *
 * <p>该模块<b>刻意没有 {@code @Scheduled}</b>（补偿统一由 web-admin 的 {@code sys_job} 驱动，便于前台改频率、
 * 留 {@code SYS_JOB_LOG}、单副本可控）。<b>NEVER 为了省事回头在 blacklist-server 里加 {@code @Scheduled}。</b>
 */
@Component("blacklistChannelSyncQuartzTask")
public class BlacklistChannelSyncQuartzTask {

    private static final Logger log = LoggerFactory.getLogger(BlacklistChannelSyncQuartzTask.class);

    private final BlacklistClient blacklistClient;

    public BlacklistChannelSyncQuartzTask(BlacklistClient blacklistClient) {
        this.blacklistClient = blacklistClient;
    }

    /** 补推加黑通知。前台调用目标：blacklistChannelSyncQuartzTask.compensateAdd()。 */
    public void compensateAdd() {
        QuartzTraceUtils.runWithTrace(traceId -> {
            OutboxScan.Result result = blacklistClient.compensateChannelSyncAdd(
                    null, QuartzTraceUtils.traceHeaders(traceId));
            logResult("加黑", result);
        });
    }

    /** 补推解黑通知。前台调用目标：blacklistChannelSyncQuartzTask.compensateRelease()。 */
    public void compensateRelease() {
        QuartzTraceUtils.runWithTrace(traceId -> {
            OutboxScan.Result result = blacklistClient.compensateChannelSyncRelease(
                    null, QuartzTraceUtils.traceHeaders(traceId));
            logResult("解黑", result);
        });
    }

    /**
     * 统一收口两个方向的结果判定。
     *
     * <p>响应为 {@code null} MUST 抛异常：静默返回会让调度日志记成成功，于是「补偿根本没跑通」看起来一切正常。
     *
     * <p>但 {@code failed > 0} <b>只打 ERROR、NEVER 抛异常</b> —— 不可达导致的失败下一轮还会重入，
     * 抛出去只会把 {@code SYS_JOB_LOG} 刷成一片红、真正需要人工介入的那类（业务拒绝已落 REJECTED 终态、
     * 扫表再也捞不到）反倒被淹掉。要人工核对的判据是日志里的 failed 数，不是任务状态。
     */
    private void logResult(String direction, OutboxScan.Result result) {
        if (result == null) {
            throw new IllegalStateException(direction + "通知补偿接口未返回响应");
        }
        if (result.failed() > 0) {
            log.error("黑名单{}通知补偿存在失败记录, MUST 人工核对是否已落 REJECTED 终态, scanned={}, success={}, failed={}",
                    direction, result.scanned(), result.success(), result.failed());
            return;
        }
        log.info("黑名单{}通知补偿完成, scanned={}, success={}, failed={}",
                direction, result.scanned(), result.success(), result.failed());
    }
}
