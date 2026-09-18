package com.chinasofti.huateng.paysign.port;

import com.chinasofti.huateng.rpc.outcome.RpcOutcome;

/**
 * 支付域看**闸机域「扣费状态收敛」方向**的窄接口（防腐层，2026-09-17，ADR-D119）。
 *
 * <p>与 {@link UnsettledOrderPort} <b>刻意分成两个端口</b>：那个服务解约链路（查欠费），
 * 这个服务支付回调链路（回写扣费终态），两条链路没有交集。按 ADR-D95「按不相交依赖簇拆分」，
 * 合成一个 {@code GateTxnPayPort} 只会让任一条链路的改动都要读另一条的方法签名。
 * <b>NEVER 因为「都是打 gate-txn-pay」就把两者合并。</b>
 */
public interface DebitSyncPort {

    /**
     * 通知闸机域把 {@code GATE_TXN_PAY.DEBIT_STATUS} 收敛到终态。
     *
     * <p>返回 {@link RpcOutcome} 而不是 boolean：调用点要据此决定「让支付中心重推」还是「本笔收口」，
     * 而「对端业务拒绝」与「对端没答」这两件事的重推价值完全不同（前者重推一万次也不会变）。
     * 本方法<b>NEVER 向外抛异常</b>，异常一律收成 {@link RpcOutcome.Unreachable}。
     */
    RpcOutcome syncDebitStatus(String orderNo, String payStatus, String remark);
}
