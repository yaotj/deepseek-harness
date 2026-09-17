package com.chinasofti.huateng.gatetxnpay.service;

/** 离线码金额补偿的对外入口，由 {@code OfflineFareRecoveryProcessor} 的 {@code @Scheduled} 驱动。 */
public interface OfflineFareRecoveryService {

    /**
     * 扫一轮待重算订单并推进。
     *
     * @param limit 单轮上限，{@code <=0} 落默认 50、超 200 收到 200。
     * @param lookbackDays 回溯天数，{@code <=0} 落默认 7。
     * @return 本轮成功推进（已重算 + 已抢占 + 已发起扣款）的笔数。
     */
    int recoverOfflineFarePendingOrders(int limit, int lookbackDays);
}
