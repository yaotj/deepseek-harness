package com.chinasofti.huateng.paysign.service;

import com.chinasofti.huateng.model.app.RequestRefundReqDTO;
import com.chinasofti.huateng.model.app.RequestRefundResult;
import com.chinasofti.huateng.model.paysign.CompensateNotifyRespDTO;

/**
 * 退款领域入口：退款发起（支付 API 3.1）与两套退款补偿。
 *
 * <p>2026-09-15 由 {@link PaymentDomainService} 拆出（纯搬迁）。拆分前那个接口同时挂着
 * 支付发起、支付回调、退款发起与两个退款补偿五个入口，实现类 1166 行。</p>
 *
 * <p><b>退款账本的唯一真相是 {@code PAY_REFUND_DETAIL}</b>；{@code PAY_TXN_DETAIL} 的
 * {@code REFUND_AMOUNT} / {@code REFUND_STATUS} 只是**派生汇总**，只能由
 * {@code PayTxnDetailMapper.updateRefundSummary} 按明细全量重算，<b>NEVER 累加式更新</b>。</p>
 */
public interface RefundDomainService {

    /**
     * 支付 API 3.1 请求退款。
     *
     * <p>调用方只传 orderNo/refundAmount；pay-sign 根据原支付订单补齐 merchantOrderNo，
     * 生成 refundOrderNo，并负责退款明细入库和原支付订单退款汇总回写。</p>
     */
    RequestRefundResult requestRefund(RequestRefundReqDTO request);

    /**
     * 退款回查补偿：扫一批停在 {@code PROCESSING} 的 {@code PAY_REFUND_DETAIL}，
     * 逐条拿支付中心 §3.2 refundQuery 收口，并在收口成功后重算原支付订单的退款汇总。
     *
     * <p>它是 {@code requestRefund} 摘掉 {@code @Transactional}（批次 5B）的**配套补偿**：
     * 那条链路最坏会停在「退款已发出、本地 PROCESSING」，此处是唯一的收口出口。
     * 触发方是 web-admin 的 Quartz，入口 {@code POST /internal/payment/compensateRefundQuery}，
     * 可反复调用直到 {@code scanned} 为 0；<b>本模块 NEVER 自带 {@code @Scheduled}</b>。</p>
     */
    CompensateNotifyRespDTO compensateRefundQuery();

    /**
     * 退款汇总跨表对账补偿：扫一批 {@code PAY_REFUND_DETAIL}（唯一账本）与
     * {@code PAY_TXN_DETAIL.REFUND_AMOUNT} / {@code REFUND_STATUS}（汇总）不一致的原支付订单，
     * 逐单重算汇总。
     *
     * <p>它补的是 {@code requestRefund} 链路<b>第 9 步</b>（{@code updateRefundSummary}）
     * 失败或漏跑留下的窟窿：明细已 {@code SUCCESS}、汇总没跟上，账面上
     * 「可退金额 = 已付 - 已退」偏大。此前<b>没有任何补偿覆盖这一步</b>。</p>
     *
     * <p><b>它与 {@link #compensateRefundQuery()} 是两件不同的事，NEVER 合并</b>：
     * 那个把停在 {@code PROCESSING} 的退款推到终态、<b>会出网</b>调支付中心 §3.2；
     * 本方法<b>不出网</b>，只做本地两表的汇总重算。</p>
     *
     * <p>扫出的两类结果处置<b>相反</b>：原支付订单存在的（A 类）重算即收口；
     * 明细有 {@code SUCCESS} 而 {@code PAY_TXN_DETAIL} 里根本没有该 {@code ORDER_NO} 的（B 类）
     * <b>一行都不改</b>，{@code updateRefundSummary} 对它影响 0 行，
     * <b>MUST NOT 当成修好</b>，只记 WARN 并计入 {@code skipped} 等人工。</p>
     *
     * <p>触发方是 web-admin 的 Quartz {@code sys_job}，入口
     * {@code POST /internal/payment/compensateRefundSummary}，可反复调用直到 {@code scanned} 为 0；
     * <b>本模块 NEVER 自带 {@code @Scheduled}</b>。</p>
     */
    CompensateNotifyRespDTO compensateRefundSummary();
}
