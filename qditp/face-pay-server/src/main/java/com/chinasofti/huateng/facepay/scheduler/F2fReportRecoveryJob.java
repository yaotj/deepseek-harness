package com.chinasofti.huateng.facepay.scheduler;

import com.chinasofti.huateng.facepay.service.F2fReportRecovery;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 出票上报后续动作的补偿任务。
 *
 * <p>加上本类，face-pay-server 的 {@code @Scheduled} <b>实测共 7 个、分布在 6 个类里</b>
 * （通知投递 / 退款回查 / 订单过期 / 设备离线 / 补款 converge / 补款 close / 本类）。
 * 仓库文档里「4 个」那个数字是补款那两个（ADR-D103）加进来之前写的，已过期。</p>
 *
 * <p>补偿逻辑本身在 {@link F2fReportRecovery}，本类只剩「多久扫一次、单轮多少条、静默期多长」
 * 三个决策，形态与 {@code F2fNotifyJob} 一致。</p>
 *
 * <p><b>静默期默认 300 秒，NEVER 调到小于设备上报请求的最长耗时。</b>出票上报链路里含
 * 支付中心退款调用，正常几百毫秒、超时可到十几秒；静默期太短会让本任务与首报<b>并发</b>
 * 重放同一行 —— 三步虽都幂等、不会二次出款，但会白发一次支付中心请求并在日志里制造
 * 两份看起来矛盾的记录，排查时极易误判成「重复退款」。</p>
 *
 * <p><b>多副本无锁</b>，与本模块其余定时任务同款取舍，face-pay-server <b>MUST 单副本</b>。</p>
 */
@Component
public class F2fReportRecoveryJob {

    private final F2fReportRecovery recovery;

    private final int batchLimit;

    private final long staleSeconds;

    public F2fReportRecoveryJob(F2fReportRecovery recovery,
                               @Value("${f2f.report.scanLimit:100}") int batchLimit,
                               @Value("${f2f.report.staleSeconds:300}") long staleSeconds) {
        this.recovery = recovery;
        this.batchLimit = batchLimit;
        this.staleSeconds = staleSeconds;
    }

    @Scheduled(fixedDelayString = "${f2f.report.scanIntervalMs:120000}",
            initialDelayString = "${f2f.report.scanInitialDelayMs:60000}")
    public void resumeDueReports() {
        recovery.resumeDue(batchLimit, staleSeconds);
    }
}
