package com.chinasofti.huateng.gatetxnpay.service.impl;

import com.chinasofti.huateng.gatetxnpay.constant.DebitStatus;
import com.chinasofti.huateng.gatetxnpay.entity.GateTxnPay;
import com.chinasofti.huateng.gatetxnpay.mapper.GateTxnPayMapper;
import com.chinasofti.huateng.gatetxnpay.paysign.PaySignInitiator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Predicate;

/**
 * 甲方需求「行程扣费重试」：把扣费未收口的过闸订单按批重新发起免密扣款。
 *
 * <p><b>2026-09-20 起按渠道拆成两条独立任务</b>（业主要求，见 ADR-D149）：
 * {@link #retryDefaultChannelDebits()} 只重试非支付宝渠道、{@link #retryAlipayChannelDebits()} 只重试支付宝出行。
 * 拆分的实质理由不是「便于分别配 cron」，而是**两类单子的扣费出口完全不同**：
 * {@code PaySignInitiator#converge} 按 {@code IssueChannelCodeEnum#isAlipay} 分流，非支付宝打 pay-sign（支付中心），
 * 支付宝打 alipay-pay-sign。混在一条任务里时，一侧渠道整体故障会连带拖慢另一侧、且两者共用一个批量上限。
 * <p>两支的渠道谓词互补且覆盖全集（见 {@code GateTxnPayMapper#selectBatchRetryCandidates} 的 Javadoc），
 * **NEVER 把非支付宝那支改成 {@code ISSUE_CHANNEL_CODE = '01'}** —— 该列可空，那样写会漏掉空值行。
 *
 * <p>业主已裁决**包含终态 {@code FAIL}**（见 {@link DebitStatus#isBatchRetryable}）。因此本类的两道闸
 * 全靠 {@code GATE_TXN_PAY} 上那四个记账列，**NEVER 去掉任何一道**：
 * <ul>
 *   <li>{@code DEBIT_RETRY_TIMES < maxTimes} —— 否则同一批死单每天被永久重扣；</li>
 *   <li>{@code TXN_DATE} 回溯窗口 —— 否则每天把几个月前的死单一起捞出来，月分区表上是全表级代价。</li>
 * </ul>
 * 这两个参数连同退避分钟数是**两条任务共用**的（业主选择），只有开关与单批条数按渠道分开。
 *
 * <p>本类刻意不带 {@code @Transactional}（每笔都要调支付中心），NEVER 加。抢占与记账收口在
 * {@code prepareBatchRetry} 一条 UPDATE 里，扣费结果由 {@code PaySignInitiator} 自己回写。
 *
 * <p><b>2026-09-21 起本类承载三条任务，第三条是 {@link #retryRecentUnpaidDebits()}</b>
 * （`sys_job` 345 补站扣费周期查询更新，每分钟一轮、只看近 N 分钟、不分渠道、**不记账**，见 ADR-D154；2026-09-21 三条任务改号：133→220、134→255、135→265→235→345）。
 * 三条共用 {@link #runRound} 的逐笔骨架与 {@code maxTimes}，抢占策略各自独立：
 * **NEVER 把第三条的抢占换成 {@code prepareBatchRetry}** —— 那会在 10 分钟内把上面两条的次数预算烧光。
 */
@Service
public class DebitRetryProcessor {

    /** 支付中心业务拒绝：重试一万次也不会成功，等人工看签约。 */
    private static final String FAIL_CODE_BIZ_REJECTED = "BIZ_REJECTED";

    /** 未获答复：下一轮继续。 */
    private static final String FAIL_CODE_UNREACHABLE = "UNREACHABLE";

    private static final Logger log = LoggerFactory.getLogger(DebitRetryProcessor.class);

    private final GateTxnPayMapper gateTxnPayMapper;

    private final PaySignInitiator paySignInitiator;

    private final boolean defaultChannelEnabled;

    private final int defaultChannelBatchSize;

    private final boolean alipayChannelEnabled;

    private final int alipayChannelBatchSize;

    private final int maxTimes;

    private final int lookbackDays;

    private final int backoffMinutes;

    private final boolean recentEnabled;

    private final int recentBatchSize;

    private final int recentWindowMinutes;

    private final int recentMinAgeSeconds;

    public DebitRetryProcessor(GateTxnPayMapper gateTxnPayMapper,
                               PaySignInitiator paySignInitiator,
                               @Value("${gate.debitRetry.default.enabled:true}") boolean defaultChannelEnabled,
                               @Value("${gate.debitRetry.default.batchSize:200}") int defaultChannelBatchSize,
                               @Value("${gate.debitRetry.alipay.enabled:true}") boolean alipayChannelEnabled,
                               @Value("${gate.debitRetry.alipay.batchSize:200}") int alipayChannelBatchSize,
                               @Value("${gate.debitRetry.maxTimes:5}") int maxTimes,
                               @Value("${gate.debitRetry.lookbackDays:7}") int lookbackDays,
                               @Value("${gate.debitRetry.backoffMinutes:720}") int backoffMinutes,
                               @Value("${gate.debitRetry.recent.enabled:true}") boolean recentEnabled,
                               @Value("${gate.debitRetry.recent.batchSize:200}") int recentBatchSize,
                               @Value("${gate.debitRetry.recent.windowMinutes:10}") int recentWindowMinutes,
                               @Value("${gate.debitRetry.recent.minAgeSeconds:60}") int recentMinAgeSeconds) {
        this.gateTxnPayMapper = gateTxnPayMapper;
        this.paySignInitiator = paySignInitiator;
        this.defaultChannelEnabled = defaultChannelEnabled;
        this.defaultChannelBatchSize = defaultChannelBatchSize;
        this.alipayChannelEnabled = alipayChannelEnabled;
        this.alipayChannelBatchSize = alipayChannelBatchSize;
        this.maxTimes = maxTimes;
        this.lookbackDays = lookbackDays;
        this.backoffMinutes = backoffMinutes;
        this.recentEnabled = recentEnabled;
        this.recentBatchSize = recentBatchSize;
        this.recentWindowMinutes = recentWindowMinutes;
        this.recentMinAgeSeconds = recentMinAgeSeconds;
    }

    /**
     * 跑一轮**非支付宝渠道**的批量重试（扣费走 pay-sign / 支付中心）。对应 {@code sys_job} 220。
     *
     * @return {@code -1} 开关未开；{@code -2} 扫表异常；否则为本轮真正重新发起的笔数
     */
    public int retryDefaultChannelDebits() {
        return retry(false, "非支付宝渠道", defaultChannelEnabled, defaultChannelBatchSize);
    }

    /**
     * 跑一轮**支付宝出行渠道**的批量重试（扣费走 alipay-pay-sign）。对应 {@code sys_job} 255。
     *
     * @return {@code -1} 开关未开；{@code -2} 扫表异常；否则为本轮真正重新发起的笔数
     */
    public int retryAlipayChannelDebits() {
        return retry(true, "支付宝出行渠道", alipayChannelEnabled, alipayChannelBatchSize);
    }

    /**
     * 跑一轮**近 N 分钟未扣费订单**的重试（甲方需求「补站扣费周期查询更新」）。对应 {@code sys_job} 345。
     *
     * <p>与上面那两条按渠道拆开的日跑任务**并行、职责不重叠**，三处差别都是刻意的：
     * <ul>
     *   <li><b>不分渠道</b>（业主选择）—— 它是「全量未扣费单的分钟级快速轮」，出口分流仍由
     *       {@code PaySignInitiator#converge} 按 {@code ISSUE_CHANNEL_CODE} 自己决定；</li>
     *   <li><b>多捞 {@code INIT}</b> —— 本任务的核心场景是「落单后扣费压根没发起」
     *       （`GateFarePaymentOrchestrator` 的 RPC 异常只记日志、不改状态），那两条日跑任务
     *       只认 {@code RETRY / FAIL}，这种单它们永远捞不到；</li>
     *   <li><b>不记账</b> —— {@code DEBIT_RETRY_TIMES} 不 +1、{@code DEBIT_NEXT_RETRY_TIME} 不后移
     *       （业主裁决「不设退避，靠 10 分钟窗口 + 状态 CAS 兜」，见 ADR-D154）。</li>
     * </ul>
     *
     * <p><b>已知代价，NEVER 当成缺陷去「修」成静默跳过</b>：不设退避 ⇒ 一笔落 {@code RETRY}
     * （对端不可达）的单在窗口内会被最多 {@code windowMinutes / cron 周期} 次重新发起。
     * 真正的防线只有两道，**删任何一道都是资损方向**：{@code minAgeSeconds} 下界（躲开正在走 RPC 的单）
     * 与 {@code windowMinutes} 上界（出窗即交给 220 / 255 按 720 分钟退避慢跑）。
     * 要收紧只能调这两个键或 cron，**NEVER 在这里加记账**（会烧光那两条任务的次数预算）。
     *
     * @return {@code -1} 开关未开；{@code -2} 扫表异常；否则为本轮真正重新发起的笔数
     */
    public int retryRecentUnpaidDebits() {
        if (!recentEnabled) {
            log.info("近{}分钟未扣费重试开关未开启，本轮跳过", recentWindowMinutes);
            return -1;
        }
        List<GateTxnPay> candidates;
        try {
            candidates = gateTxnPayMapper.selectRecentUnpaidCandidates(recentWindowMinutes,
                    recentMinAgeSeconds, maxTimes, recentBatchSize);
        } catch (RuntimeException e) {
            log.error("近{}分钟未扣费重试扫表失败, minAgeSeconds={}", recentWindowMinutes, recentMinAgeSeconds, e);
            return -2;
        }
        String taskDesc = "近" + recentWindowMinutes + "分钟未扣费";
        if (candidates.isEmpty()) {
            log.info("{}重试本轮无候选, minAgeSeconds={}, maxTimes={}",
                    taskDesc, recentMinAgeSeconds, maxTimes);
            return 0;
        }
        int retried = runRound(taskDesc, candidates, this::prepareRecent);
        log.info("{}重试本轮完成, 候选={}, 已重发={}, 窗口={}分钟, 最小年龄={}秒, 单批={}",
                taskDesc, candidates.size(), retried, recentWindowMinutes, recentMinAgeSeconds, recentBatchSize);
        return retried;
    }

    private int retry(boolean alipayChannel, String channelDesc, boolean enabled, int batchSize) {
        if (!enabled) {
            log.info("扣费批量重试开关未开启，本轮跳过, 渠道={}", channelDesc);
            return -1;
        }
        LocalDate today = LocalDate.now();
        String startDate = today.minusDays(lookbackDays).format(DateTimeFormatter.BASIC_ISO_DATE);
        String endDate = today.format(DateTimeFormatter.BASIC_ISO_DATE);

        List<GateTxnPay> candidates;
        try {
            candidates = gateTxnPayMapper.selectBatchRetryCandidates(alipayChannel, startDate, endDate,
                    maxTimes, batchSize);
        } catch (RuntimeException e) {
            log.error("扣费批量重试扫表失败, 渠道={}, startDate={}, endDate={}", channelDesc, startDate, endDate, e);
            return -2;
        }
        if (candidates.isEmpty()) {
            log.info("扣费批量重试本轮无候选, 渠道={}, startDate={}, endDate={}, maxTimes={}",
                    channelDesc, startDate, endDate, maxTimes);
            return 0;
        }

        int retried = runRound(channelDesc, candidates, this::prepare);
        log.info("扣费批量重试本轮完成, 渠道={}, 候选={}, 已重发={}, 窗口={}~{}, 上限={}次, 退避={}分钟, 单批={}",
                channelDesc, candidates.size(), retried, startDate, endDate,
                maxTimes, backoffMinutes, batchSize);
        return retried;
    }

    /** 逐笔「抢占 → 重新发起 → 记落点」，返回真正重新发起的笔数。三条任务共用，抢占策略由 {@code claim} 决定。 */
    private int runRound(String taskDesc, List<GateTxnPay> candidates, Predicate<GateTxnPay> claim) {
        int retried = 0;
        int skipped = 0;
        int failed = 0;
        for (GateTxnPay order : candidates) {
            try {
                if (!claim.test(order)) {
                    skipped++;
                    continue;
                }
                String landed = paySignInitiator.retryAndConverge(order);
                retried++;
                recordLanding(order, landed);
            } catch (RuntimeException e) {
                failed++;
                log.error("扣费重试异常, 任务={}, orderNo={}, cardId={}",
                        taskDesc, order.getOrderNo(), order.getCardId(), e);
            }
        }
        if (skipped > 0 || failed > 0) {
            log.info("扣费重试逐笔统计, 任务={}, 跳过={}, 异常={}", taskDesc, skipped, failed);
        }
        return retried;
    }

    /** 抢占并记账；返回 false 说明这一笔已被别人处理或不再满足条件，本轮跳过。 */
    private boolean prepare(GateTxnPay order) {
        int prepared = gateTxnPayMapper.prepareBatchRetry(order.getOrderNo(), order.getTxnDate(),
                maxTimes, backoffMinutes, "批量重试已发起，等支付中心结果");
        if (prepared == 0) {
            log.warn("扣费批量重试未抢到该笔（已被处理 / 次数用尽 / 退避未到）, orderNo={}", order.getOrderNo());
            return false;
        }
        order.setDebitStatus(DebitStatus.RETRY.code());
        return true;
    }

    /** 近 N 分钟那条的抢占：只归一状态、**不记账**（理由见 {@link #retryRecentUnpaidDebits()}）。 */
    private boolean prepareRecent(GateTxnPay order) {
        int prepared = gateTxnPayMapper.prepareRecentRetry(order.getOrderNo(), order.getTxnDate(),
                maxTimes, "周期查询更新已发起，等支付结果");
        if (prepared == 0) {
            log.warn("近{}分钟未扣费重试未抢到该笔（已被推进 / 次数用尽 / 退避未到 / 转成离线码待重算）, orderNo={}",
                    recentWindowMinutes, order.getOrderNo());
            return false;
        }
        order.setDebitStatus(DebitStatus.RETRY.code());
        return true;
    }

    /** 落点不是 {@code PROCESSING} 时补一条语义码，明细原因由 {@code REMARK} 承载。 */
    private void recordLanding(GateTxnPay order, String landed) {
        if (DebitStatus.PROCESSING.is(landed)) {
            return;
        }
        String failCode = DebitStatus.FAIL.is(landed) ? FAIL_CODE_BIZ_REJECTED : FAIL_CODE_UNREACHABLE;
        gateTxnPayMapper.updateDebitFailInfo(order.getOrderNo(), order.getTxnDate(), failCode,
                "批量重试落 " + landed + "，详见 REMARK");
        log.warn("扣费批量重试未被受理, orderNo={}, 落点={}, failCode={}", order.getOrderNo(), landed, failCode);
    }
}
