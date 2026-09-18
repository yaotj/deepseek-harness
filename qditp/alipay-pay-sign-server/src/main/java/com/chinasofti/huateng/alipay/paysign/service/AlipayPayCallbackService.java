package com.chinasofti.huateng.alipay.paysign.service;

import com.chinasofti.huateng.common.response.AlipayCommonResponse;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripPayNotifyReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRefundNotifyReqDTO;

/**
 * 支付中心**入向回调聚合**的唯一入口（2026-09-18 迁移第 5、6 条，接在 `requestPay` 之后）。
 *
 * <p>只承载 {@code docs/external/支付中心网关接口文档.md} §5 里对本模块适用的两条：支付结果回调与退款结果回调。
 * 两条放同一个接口是因为**依赖簇相交**：都只写同一张凭据表 {@code ALIPAY_PAY_CALLBACK_LOG}、共用
 * {@code CALLBACK_TYPE} 这一列分组、共用「落证据 → 处置 → 回写处置状态」这套骨架。
 *
 * <p><b>NEVER 把出向通知塞进来</b>：黑名单变更、销卡结果那两条是**我方发出去的**、判据与本方向完全不同，
 * 它们在 {@code service/impl/notify/PaymentNotifyAdapter}。契约 §5.3 签约 / §5.4 解约结果对本模块不适用
 * （本模块没配那两条出向 URL，属 {@code pay-sign-server}）。
 *
 * <p>实现刻意不带 {@code @Transactional}：支付回调链路内有一次出网（通知 gate-txn-pay 收敛扣费状态），
 * 事务包住会把行锁持有时长拉成对端响应时长；更要紧的是**回滚会把「留证据」那条 INSERT 一起丢掉**，
 * 而限次判定正是按那张表计数的 —— 证据没了，限次永远不触发，支付中心会一直重推。
 */
public interface AlipayPayCallbackService {

    /**
     * 契约 §5.1 支付结果回调（{@code POST /api/payment/payNotify}）。
     *
     * <p>这条 URL 由 {@code pay.center.callback-url} 下发给支付中心，**改它等于改已在用的对外契约**。
     * 涉及资金收口，任何改动 MUST 人工复核。
     */
    AlipayCommonResponse handlePayNotify(AlipayTripPayNotifyReqDTO request);

    /**
     * 契约 §5.2 退款结果回调（{@code POST /api/payment/refundNotify}）。
     *
     * <p><b>当前只落回调凭据就返成功，退款明细与汇总的回写刻意未接线</b>：退款收口走
     * {@code compensateRefundQuery} 主动回查那条路。这里返非 {@code 0000} 会让支付中心一直重推一笔
     * 我方本就不按回调收口的退款；这里就地回写又会与回查形成两个写入方互相覆盖。
     * <b>NEVER 顺手把回写接上</b> —— 先要裁决「回查与回调谁优先」。
     */
    AlipayCommonResponse handleRefundNotify(AlipayTripRefundNotifyReqDTO request);
}
