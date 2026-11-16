package com.chinasofti.huateng.facepay.scheduler;

import com.chinasofti.huateng.facepay.service.supplement.SupplementOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SupplementOrderCloseProcessor {

    private static final Logger log = LoggerFactory.getLogger(SupplementOrderCloseProcessor.class);

    /** INIT 单超时（分钟）：预下单未完成（用户未拉起收银台）即关。 */
    private static final int INIT_TIMEOUT_MINUTES = 30;

    /** PROCESSING 单超时（分钟）：支付中心侧 24h 仍非终态，本地兜底关单。 */
    private static final int PROCESSING_TIMEOUT_MINUTES = 24 * 60;

    private static final int BATCH_LIMIT = 100;

    private final SupplementOrderService supplementOrderService;

    public SupplementOrderCloseProcessor(SupplementOrderService supplementOrderService) {
        this.supplementOrderService = supplementOrderService;
    }

    @Scheduled(cron = "${supplement.scheduler.converge-cron:0 */5 * * * ?}")
    public int converge() {
        int converged = 0;
        try {
            converged = supplementOrderService.convergePendingOrders(BATCH_LIMIT);
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
            int initClosed = supplementOrderService.closeTimeoutOrders(INIT_TIMEOUT_MINUTES, BATCH_LIMIT);
            int processingClosed = supplementOrderService.closeStaleProcessingOrders(
                    PROCESSING_TIMEOUT_MINUTES, BATCH_LIMIT);
            closed = initClosed + processingClosed;
            log.info("补款超时关单完成, initClosed={}, processingClosed={}", initClosed, processingClosed);
        } catch (RuntimeException e) {
            log.error("补款超时关单异常", e);
        }
        return closed;
    }
}
