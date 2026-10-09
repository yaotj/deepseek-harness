package com.chinasofti.huateng.gatetxnpay.service.impl;

import com.chinasofti.huateng.gatetxnpay.service.OfflineFareRecoveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 离线码出站时金额重算失败订单的补偿入口。
 *
 * <p>NEVER 加回 {@code @Scheduled}，已改由 web-admin {@code sys_job} 295 触发（2026-09-21 由 120 改号为 295，ADR-D80）。
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

    /** 跑一轮补偿，返回本轮已推进的笔数。 */
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
