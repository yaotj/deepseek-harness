package com.chinasofti.huateng.facepay.scheduler;

import com.chinasofti.huateng.facepay.service.supplement.SupplementOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SupplementOrderCloseProcessor {

    private static final Logger log = LoggerFactory.getLogger(SupplementOrderCloseProcessor.class);

    private final SupplementOrderService supplementOrderService;

    public SupplementOrderCloseProcessor(SupplementOrderService supplementOrderService) {
        this.supplementOrderService = supplementOrderService;
    }

    @Scheduled(cron = "${supplement.scheduler.converge-cron:0 */5 * * * ?}")
    public int converge() {
        int converged = 0;
        try {
            converged = supplementOrderService.convergePendingOrders(100);
            log.info("补款收敛完成, converged={}", converged);
        } catch (RuntimeException e) {
            log.error("补款收敛异常", e);
        }
        return converged;
    }

    @Scheduled(cron = "${supplement.scheduler.close-cron:0 */10 * * * ?}")
    public int closeTimeout() {
        int closed = 0;
        try {
            closed = supplementOrderService.closeTimeoutOrders(30, 100);
            log.info("补款超时关单完成, closed={}", closed);
        } catch (RuntimeException e) {
            log.error("补款超时关单异常", e);
        }
        return closed;
    }
}
