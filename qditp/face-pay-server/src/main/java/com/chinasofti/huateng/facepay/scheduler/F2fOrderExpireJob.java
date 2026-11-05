package com.chinasofti.huateng.facepay.scheduler;

import com.chinasofti.huateng.facepay.entity.F2fOrder;
import com.chinasofti.huateng.facepay.service.F2fOrderExpireService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class F2fOrderExpireJob {

    private static final Logger log = LoggerFactory.getLogger(F2fOrderExpireJob.class);

    private final F2fOrderExpireService expireService;

    private final int batchLimit;

    public F2fOrderExpireJob(F2fOrderExpireService expireService,
                             @Value("${f2f.order.expireScanLimit:200}") int batchLimit) {
        this.expireService = expireService;
        this.batchLimit = batchLimit;
    }

    @Scheduled(fixedDelayString = "${f2f.order.expireScanIntervalMs:30000}",
            initialDelayString = "${f2f.order.expireScanInitialDelayMs:15000}")
    public void reconcileExpiredOrders() {
        List<F2fOrder> candidates;
        try {
            candidates = expireService.loadExpiredCandidates(batchLimit);
        } catch (RuntimeException e) {
            log.error("过期订单扫表失败", e);
            return;
        }
        if (candidates.isEmpty()) {
            return;
        }
        int resolved = 0;
        int pending = 0;
        int failed = 0;
        for (F2fOrder order : candidates) {
            try {
                if (expireService.reconcileExpiredOrder(order)) {
                    resolved++;
                } else {
                    pending++;
                }
            } catch (RuntimeException e) {
                failed++;
                log.error("过期订单收口异常, orderNo={}", order.getOrderNo(), e);
            }
        }
        log.info("过期订单收口完成, 候选={}, 已收口={}, 待重试={}, 异常={}",
                candidates.size(), resolved, pending, failed);
        warnStaleOrders();
    }

    private void warnStaleOrders() {
        try {
            long stale = expireService.countStaleExpiredOrders();
            if (stale > 0) {
                log.error("有 {} 笔过期订单已超放弃窗口仍停在 CREATED/PAYING，不再自动收口，需人工判定", stale);
            }
        } catch (RuntimeException e) {
            log.warn("统计超窗未收口订单失败", e);
        }
    }
}
