package com.chinasofti.huateng.alipay.paysign.port;

import com.chinasofti.huateng.rpc.outcome.RpcOutcome;

/**
 * 支付宝渠道看**闸机域「扣费状态收敛」方向**的窄接口（防腐层，ADR-D131）。
 *
 * <p>与 {@link BlacklistPort} <b>刻意分成两个端口</b>：那个服务「扣款失败拉黑」链路，
 * 这个服务支付回调链路，两条链路没有交集。按「按不相交依赖簇拆分」，合成一个
 * {@code GateTxnPayPort} 只会让任一条链路的改动都要读另一条的方法签名。</p>
 */
public interface DebitSyncPort {

    /**
     * 通知闸机域把 {@code GATE_TXN_PAY.DEBIT_STATUS} 收敛到终态。
     *
     * <p>本方法 <b>NEVER 向外抛异常</b>，异常一律收成 {@link RpcOutcome.Unreachable}。</p>
     */
    RpcOutcome syncDebitStatus(String orderNo, String payStatus, String remark);
}
