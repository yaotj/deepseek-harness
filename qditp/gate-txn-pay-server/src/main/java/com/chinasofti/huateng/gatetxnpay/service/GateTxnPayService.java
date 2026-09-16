package com.chinasofti.huateng.gatetxnpay.service;

import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.gatetxnpay.model.page.BatchRefundOvertimeRequest;
import com.chinasofti.huateng.gatetxnpay.model.page.BatchRefundResult;
import com.chinasofti.huateng.gatetxnpay.model.page.GateTxnPayRefundRequest;
import com.chinasofti.huateng.model.app.RequestRefundResult;
import com.chinasofti.huateng.model.pay.GateTxnPayDebitConvergeReqDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayDebitConvergeRespDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayReqDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayRespDTO;
import com.chinasofti.huateng.model.pay.GateTxnPaySyncStatusReqDTO;

/**
 * `GATE_TXN_PAY` 的**写入侧**：出站扣费、重试、退款、状态收敛、离线码金额补偿重算。
 *
 * <p>只读查询已整段搬到 {@link GateTxnPayQueryService}、运营补数已搬到
 * {@link OriginalFareBackfillService}，分界线是**有没有写**、以及**是否属于出站扣费主链路**。
 * 本接口的每个方法都会改状态或调远端（支付中心 / 票价 / 钱包），因此**都要考虑幂等**；
 * 新增方法前 MUST 先按这条线判断该放哪边，**NEVER 把只读查询加回本接口**。
 *
 * <p>拆分只动了 Java 类型，**HTTP 端点、URL 与报文一个都没变**。
 */
public interface GateTxnPayService {
    GateTxnPayRespDTO requestPay(GateTxnPayReqDTO request);

    /**
     * 对支付失败/未支付的订单单独重试支付。
     *
     * @param orderNo 订单号
     * @return 支付结果
     */
    GateTxnPayRespDTO retryPay(String orderNo);

    ResultVO<RequestRefundResult> requestRefund(String orderNo, GateTxnPayRefundRequest request);

    /**
     * 综管台批量退超时罚金：对圈出订单逐单发起退款，金额为各自 {@code OVERTIME_AMOUNT}。
     *
     * <p>每单仍走单笔 {@link #requestRefund} 链路（日票拒退、状态白名单、金额上限全保留），
     * 单笔失败不阻断整批。本方法含支付中心 RPC，NEVER 加事务。</p>
     */
    ResultVO<BatchRefundResult> batchRefundOvertime(BatchRefundOvertimeRequest request);

    /**
     * 按支付结果回调收敛扣费状态（供 pay-sign-server RPC 调用）。
     *
     * <p>只做状态收敛，NEVER 触发扣款；SUCCESS / FAIL 终态订单不会被改写。</p>
     */
    GateTxnPayRespDTO syncDebitStatus(GateTxnPaySyncStatusReqDTO request);

    /**
     * 在线补款支付成功后收敛原行程的扣费状态（供 face-pay-server 经
     * {@code POST /internal/gate-txn-pay/debit/converge} 调用，2026-09-16 新增）。
     *
     * <p><b>为什么不复用 {@link #syncDebitStatus}</b>：那条服务支付结果回调，语义是
     * 「中间态 到 终态」，白名单不含 {@code FAIL}，且「0 行但已是同一终态」也返 {@code 0000}，
     * 把「本次真改了行」与「早已被别人收敛」压成同一结果。补款链路 MUST 区分这两者
     * —— 后者意味着本单是重复支付、需要退款。因此本方法回填
     * {@code converged} 与 {@code debitStatus} 两个字段让调用方自行判据，
     * <b>NEVER 把两条合并成一条</b>。</p>
     *
     * <p>白名单多一个 {@code FAIL}，与补款下单校验的「欠费可补」口径一致（2026-09-16 裁决）。</p>
     *
     * <p>本方法只改状态、NEVER 触发扣款，也 NEVER 调任何远端，因此可以带事务。</p>
     */
    GateTxnPayDebitConvergeRespDTO convergeDebitStatusForSupplement(GateTxnPayDebitConvergeReqDTO request);
}
