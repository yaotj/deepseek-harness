package com.chinasofti.huateng.gatetxnpay.service;

/**
 * 离线码金额补偿的对外入口，由 {@code OfflineFareRecoveryProcessor} 的 {@code @Scheduled} 驱动。
 *
 * <p>从 {@code GateTxnPayService} 拆出来的分界线是**扫的是哪一批行**：出站扣费主链路处理的是
 * 「本次请求这一笔」，本接口处理的是「历史留下的待重算批次」。前者由闸机同步触发、后者由定时任务驱动。</p>
 *
 * <p>实现 **MUST** 支持多副本并发运行（靠 {@code updateOfflineFareRecalculated} 的条件更新抢占），
 * 且返回值只计**成功推进的笔数**，**NEVER** 把跳过或失败也算进去 —— 运维靠这个数判断补偿有没有在推进。</p>
 */
public interface OfflineFareRecoveryService {

    /**
     * 扫一轮待重算订单并推进。
     *
     * @param limit        单轮上限，{@code <=0} 落默认 50、超 200 收到 200
     * @param lookbackDays 回溯天数，{@code <=0} 落默认 7
     * @return 本轮成功推进（已重算 + 已抢占 + 已发起扣款）的笔数
     */
    int recoverOfflineFarePendingOrders(int limit, int lookbackDays);
}
