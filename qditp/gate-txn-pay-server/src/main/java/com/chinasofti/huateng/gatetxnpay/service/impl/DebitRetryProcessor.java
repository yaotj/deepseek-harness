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


@Service
public class DebitRetryProcessor {

    private static final String FAIL_CODE_BIZ_REJECTED = "BIZ_REJECTED";

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

    private final int recentMinAgeSeconds;

    public DebitRetryProcessor(GateTxnPayMapper gateTxnPayMapper,
                               PaySignInitiator paySignInitiator,
                               @Value("${gate.debitRetry.default.enabled:true}") boolean defaultChannelEnabled,
                               @Value("${gate.debitRetry.default.batchSize:200}") int defaultChannelBatchSize,
                               @Value("${gate.debitRetry.alipay.enabled:true}") boolean alipayChannelEnabled,
                               @Value("${gate.debitRetry.alipay.batchSize:200}") int alipayChannelBatchSize,
                               @Value("${gate.debitRetry.maxTimes:5}") int maxTimes,
                               @Value("${gate.debitRetry.lookbackDays:7}") int lookbackDays,
                               @Value("${gate.debitRetry.backoffMinutes:300}") int backoffMinutes,
                               @Value("${gate.debitRetry.recent.enabled:true}") boolean recentEnabled,
                               @Value("${gate.debitRetry.recent.batchSize:200}") int recentBatchSize,
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
        this.recentMinAgeSeconds = recentMinAgeSeconds;
    }


    public int retryDefaultChannelDebits() {
        return retry(false, "非支付宝渠道", defaultChannelEnabled, defaultChannelBatchSize);
    }


    public int retryAlipayChannelDebits() {
        return retry(true, "支付宝出行渠道", alipayChannelEnabled, alipayChannelBatchSize);
    }


    public int retryRecentUnpaidDebits() {
        if (!recentEnabled) {
            log.info("卡单（INIT）补发开关未开启，本轮跳过");
            return -1;
        }
        List<GateTxnPay> candidates;
        try {
            candidates = gateTxnPayMapper.selectRecentUnpaidCandidates(recentMinAgeSeconds, recentBatchSize);
        } catch (RuntimeException e) {
            log.error("卡单（INIT）补发扫表失败, minAgeSeconds={}", recentMinAgeSeconds, e);
            return -2;
        }
        String taskDesc = "卡在INIT的单";
        if (candidates.isEmpty()) {
            log.info("{}补发本轮无候选, minAgeSeconds={}", taskDesc, recentMinAgeSeconds);
            return 0;
        }
        int retried = runRound(taskDesc, candidates, this::prepareRecent,
                com.chinasofti.huateng.gatetxnpay.paysign.AlipayTripPayRequestFactory.PAY_TRIGGER_FIRST);
        log.info("{}补发本轮完成, 候选={}, 已重发={}, 最小年龄={}秒, 单批={}",
                taskDesc, candidates.size(), retried, recentMinAgeSeconds, recentBatchSize);
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

        int retried = runRound(channelDesc, candidates, this::prepare,
                com.chinasofti.huateng.gatetxnpay.paysign.AlipayTripPayRequestFactory.PAY_TRIGGER_COMPENSATION);
        log.info("扣费批量重试本轮完成, 渠道={}, 候选={}, 已重发={}, 窗口={}~{}, 上限={}次, 退避={}分钟, 单批={}",
                channelDesc, candidates.size(), retried, startDate, endDate,
                maxTimes, backoffMinutes, batchSize);
        return retried;
    }


    private int runRound(String taskDesc, List<GateTxnPay> candidates, Predicate<GateTxnPay> claim,
                         String payTriggerType) {
        int retried = 0;
        int skipped = 0;
        int failed = 0;
        for (GateTxnPay order : candidates) {
            try {
                if (!claim.test(order)) {
                    skipped++;
                    continue;
                }
                String landed = paySignInitiator.retryAndConverge(order, payTriggerType);
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

    private boolean prepareRecent(GateTxnPay order) {
        int prepared = gateTxnPayMapper.prepareRecentRetry(order.getOrderNo(), order.getTxnDate(),
                "周期查询更新已发起，等支付结果");
        if (prepared == 0) {
            log.warn("卡单（INIT）补发未抢到该笔（已被推进 / 转成离线码待重算）, orderNo={}",
                    order.getOrderNo());
            return false;
        }
        order.setDebitStatus(DebitStatus.RETRY.code());
        return true;
    }

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
