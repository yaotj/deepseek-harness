package com.chinasofti.huateng.alipay.paysign.service;

import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripRequestPayReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripRequestPayRespDTO;

/**
 * 支付宝渠道**扣费申请聚合**的唯一入口（2026-09-18 迁移第 4 条，接在
 * {@link AlipaySignContractService} / {@link AlipayTerminationService} 之后）。
 *
 * <p>只承载「发起一笔免密扣款」这件事：入参校验、查生效签约、落
 * {@code ALIPAY_PAY_TXN_DETAIL}、出网、按支付中心应答回写。**支付结果查询与退款不在本接口内**
 * —— 查询读的是同一张表但只回写 {@code payStatus}、退款走 {@code ALIPAY_REFUND_TXN_DETAIL}
 * 另一套状态，混进来会让「谁在推进这笔单子的状态」无法判断。
 *
 * <p><b>与前三条端点的迁移形态不同，MUST 读懂再改</b>：前三条是「旧实现在跑 → 切到新服务」，
 * 而 {@code POST /api/payment/requestPay} 这条现在跑的已经是 {@code AlipayTxnPayService}
 * （旧的 {@code PaymentRequestService} 早已失去 HTTP 入口、且全程零落库）。因此本次迁移搬的是
 * {@code AlipayTxnPayService} 的行为，**NEVER 把 {@code PaymentRequestService} 那份「零落库」
 * 的旧行为当成要保留的正确行为搬过来**。
 *
 * <p>实现刻意不带 {@code @Transactional}：链路里有一次 RPC（查订单主表取 {@code txnDate}）与一次
 * 支付中心 HTTP 调用，事务包住它们会让行锁持有时长等于对端响应时长；且「出网前那次落库
 * MUST 已经提交」，否则出网后进程挂掉就什么痕迹都不剩。
 */
public interface AlipayPayRequestService {

    /**
     * 扣费申请（{@code POST /api/payment/requestPay}，调用方 gate-txn-pay-server）。
     *
     * <p>涉及资金（发起免密扣款），任何改动 MUST 人工复核。
     */
    AlipayTripRequestPayRespDTO requestPay(AlipayTripRequestPayReqDTO request);
}
