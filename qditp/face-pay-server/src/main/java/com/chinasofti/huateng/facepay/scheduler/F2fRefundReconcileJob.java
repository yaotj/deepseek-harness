package com.chinasofti.huateng.facepay.scheduler;

import com.chinasofti.huateng.facepay.entity.F2fRefund;
import com.chinasofti.huateng.facepay.service.F2fRefundService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/** 退款收口任务。无分布式锁，face-pay-server MUST 单副本；本类不带 {@code @Transactional}（每笔都要调支付中心）。 */
@Component
public class F2fRefundReconcileJob {

    private static final Logger log = LoggerFactory.getLogger(F2fRefundReconcileJob.class);

    private final F2fRefundService refundService;

    private final int batchLimit;

    public F2fRefundReconcileJob(F2fRefundService refundService,
                                 @Value("${f2f.refund.scanLimit:100}") int batchLimit) {
        this.refundService = refundService;
        this.batchLimit = batchLimit;
    }

    @Scheduled(fixedDelayString = "${f2f.refund.scanIntervalMs:60000}",
            initialDelayString = "${f2f.refund.scanInitialDelayMs:30000}")
    public void reconcileRefunds() {
        List<F2fRefund> candidates;
        try {
            candidates = refundService.loadRetryCandidates(LocalDateTime.now(), batchLimit);
        } catch (RuntimeException e) {
            log.error("退款收口扫表失败", e);
            return;
        }
        if (candidates.isEmpty()) {
            return;
        }
        int resolved = 0;
        int pending = 0;
        int failed = 0;
        for (F2fRefund refund : candidates) {
            try {
                if (refundService.reconcileRefund(refund)) {
                    resolved++;
                } else {
                    pending++;
                }
            } catch (RuntimeException e) {
                failed++;
                log.error("退款收口异常, refundNo={}", refund.getRefundNo(), e);
            }
        }
        log.info("退款收口完成, 候选={}, 已收口={}, 待重试={}, 异常={}",
                candidates.size(), resolved, pending, failed);
    }
}
