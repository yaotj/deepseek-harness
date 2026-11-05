package com.chinasofti.huateng.facepay.service;

import com.chinasofti.huateng.facepay.entity.F2fOrder;
import com.chinasofti.huateng.facepay.mapper.F2fOrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class F2fBatchRefundService {

    public static final List<String> BIZ_SINGLE_TICKET = List.of("01");

    public static final List<String> BIZ_TOPUP = List.of("02");

    public static final List<String> BIZ_NO_CASH = List.of("04");

    private static final Logger log = LoggerFactory.getLogger(F2fBatchRefundService.class);

    private final F2fOrderMapper orderMapper;

    private final F2fRefundService refundService;

    private final int batchLimit;

    private final int silenceMinutes;

    private final int lookbackDays;

    public F2fBatchRefundService(F2fOrderMapper orderMapper, F2fRefundService refundService,
                                 @Value("${f2f.batchRefund.limit:200}") int batchLimit,
                                 @Value("${f2f.batchRefund.silenceMinutes:60}") int silenceMinutes,
                                 @Value("${f2f.batchRefund.lookbackDays:1}") int lookbackDays) {
        this.orderMapper = orderMapper;
        this.refundService = refundService;
        this.batchLimit = batchLimit;
        this.silenceMinutes = silenceMinutes;
        this.lookbackDays = lookbackDays;
    }

    public BatchRefundResult refundBatch(String taskName, List<String> bizTypes) {
        LocalDateTime now = LocalDateTime.now();
        List<F2fOrder> candidates = orderMapper.selectPaidNotFulfilled(bizTypes,
                now.minusDays(lookbackDays), now.minusMinutes(silenceMinutes), batchLimit);
        if (candidates.isEmpty()) {
            log.info("批量退款本轮无候选, task={}, bizTypes={}", taskName, bizTypes);
            return new BatchRefundResult(0, 0, 0, 0);
        }

        int submitted = 0;
        int skipped = 0;
        int failed = 0;
        for (F2fOrder order : candidates) {
            Long amount = order.getOrderAmount();
            if (amount == null || amount <= 0L) {
                skipped++;
                log.warn("订单金额非正，跳过批量退款, task={}, orderNo={}, amount={}",
                        taskName, order.getOrderNo(), amount);
                continue;
            }
            try {
                RefundOutcome outcome = refundService.refund(new RefundCommand(order.getOrderNo(), null,
                        F2fRefundService.SOURCE_DAILY_BATCH, amount, order.getTicketNum(),
                        "每日批量退未履约交易", null, null, order.getTransType(), null, null));
                if (outcome.isRejected()) {
                    skipped++;
                    log.warn("批量退款被拒, task={}, orderNo={}, outcome={}",
                            taskName, order.getOrderNo(), outcome);
                } else {
                    submitted++;
                }
            } catch (RuntimeException e) {
                failed++;
                log.error("批量退款异常, task={}, orderNo={}", taskName, order.getOrderNo(), e);
            }
        }
        log.info("批量退款本轮完成, task={}, 候选={}, 已发起={}, 跳过={}, 异常={}, 静默期={}分钟, 回溯={}天",
                taskName, candidates.size(), submitted, skipped, failed, silenceMinutes, lookbackDays);
        return new BatchRefundResult(candidates.size(), submitted, skipped, failed);
    }

    public record BatchRefundResult(int scanned, int submitted, int skipped, int failed) {
    }
}
