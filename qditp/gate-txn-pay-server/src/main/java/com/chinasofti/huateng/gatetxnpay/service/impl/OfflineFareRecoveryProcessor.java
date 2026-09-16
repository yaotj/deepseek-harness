package com.chinasofti.huateng.gatetxnpay.service.impl;

import com.chinasofti.huateng.gatetxnpay.service.OfflineFareRecoveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 离线码出站时金额重算失败订单的补偿入口。
 *
 * <p>离线码出站的金额由服务端重算（同序列号首笔进站 + 票价 + 超时费 + 换乘减免 + 钱包折扣），
 * 依赖 ticket-server 与 para-server。任一不可达时订单以
 * {@code DEBIT_STATUS='INIT'} + {@code DISCOUNT_CALC_STATUS='OFFLINE_FARE_PENDING'} 留痕，
 * 由本任务重算并补扣款——**这是「金额未定」而非「免扣费」，NEVER 按 0 金额收口。**</p>
 *
 * <p>抢占依赖 {@code updateOfflineFareRecalculated} 的条件更新，因此支持多副本并发运行，
 * 与 {@link MetroTransferPushTaskProcessor} 同一模式。</p>
 *
 * <p><b>2.0.73 起本类不再有 {@code @Scheduled}，调度移到 web-admin 的 `sys_job`</b>
 * （`gateTxnPayQuartzTask.recoverOfflineFare()`，cron `0 0/1 * * * ?`），入口是
 * {@code POST /internal/gate-txn-pay/offline-fare/recover}。**NEVER 在这里加回 `@Scheduled`** ——
 * 两套调度源互不知情，会并发发起扣款；同理 **NEVER 删 `sys_job` 那行**，删了补偿就彻底停摆。</p>
 *
 * <p><b>调度语义已从 `fixedDelay` 变成 cron，这不是等价替换</b>：原来是「上一轮跑完再等 60 秒」、
 * 保证两轮间隔≥60s；现在是墙上时钟每分钟触发，只靠 `sys_job.concurrent='1'`（禁止并发）保证不重叠。
 * 上一轮耗时接近 60 秒时，下一轮会比原行为**早最多 60 秒**开始。这在本链路无害：扣款前必须先过
 * {@code applyOfflineFareRecalculated} 的条件更新（返回 1 才继续），早跑一轮最坏只是多一次空扫。
 * <b>但 `concurrent` 那一列 NEVER 改成 '0'</b>（'0' 是允许并发），一改就是并发重复扣款。</p>
 */
@Service
public class OfflineFareRecoveryProcessor {

    private static final Logger log = LoggerFactory.getLogger(OfflineFareRecoveryProcessor.class);

    private final OfflineFareRecoveryService offlineFareRecoveryService;
    private final boolean enabled;
    private final int batchSize;
    private final int lookbackDays;

    public OfflineFareRecoveryProcessor(OfflineFareRecoveryService offlineFareRecoveryService,
                                        @Value("${gate.pay.offline-fare-recovery-enabled:true}") boolean enabled,
                                        @Value("${gate.pay.offline-fare-recovery-batch-size:50}") int batchSize,
                                        @Value("${gate.pay.offline-fare-recovery-lookback-days:7}") int lookbackDays) {
        this.offlineFareRecoveryService = offlineFareRecoveryService;
        this.enabled = enabled;
        this.batchSize = batchSize;
        this.lookbackDays = lookbackDays;
    }

    /**
     * 跑一轮补偿，返回本轮已推进的笔数。
     *
     * <p>{@code enabled=false} 时直接返回 -1 —— **用 -1 而不是 0 区分「开关关了」与「本轮没单子」**，
     * 否则调度日志里两种情况长得一样、排查「补偿为什么不动」时分不清是没数据还是被关了。</p>
     *
     * <p>异常在这里兜住只记日志（返回 -2），**不往外抛**：本方法现在由 HTTP 入口调用，
     * 抛出去会让 web-admin 侧的 `sys_job_log` 记失败——那是对的，但**本轮扫表异常属于可自愈**
     * （下一分钟再来一轮），记成调度失败会淹没真正需要人看的失败。收口判据仍在返回值里。</p>
     */
    public int recoverOfflineFarePendingOrders() {
        if (!enabled) {
            log.info("离线码金额补偿开关未开启（gate.pay.offline-fare-recovery-enabled=false），本轮跳过");
            return -1;
        }
        try {
            return offlineFareRecoveryService.recoverOfflineFarePendingOrders(batchSize, lookbackDays);
        } catch (RuntimeException e) {
            log.error("离线码金额补偿任务执行异常，本轮跳过", e);
            return -2;
        }
    }
}
