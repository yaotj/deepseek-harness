package com.chinasofti.huateng.alipay.paysign.port;

import java.util.Map;

/**
 * 支付中心（bestonepay）出网端口 —— 支付 / 退款 / 支付查询 / 退款查询四个方向，ADR-D131。
 *
 * <p>退款查询是 2026-09-20 补的第四个方向（退款回查补偿）：此前 {@code PayCenterClient.refundQuery}
 * 与 {@code pay.center.refund-query-url} 都在、却零调用方，于是「支付中心没答 / 拒绝」那支落下的
 * {@code PROCESSING} 退款明细没人回查、只能人工核。<b>NEVER 把它挪出本端口自己直调 client</b> ——
 * 那会绕过下面那条「不吞异常」的约定。</p>
 *
 * <p>与 {@link DebitSyncPort} / {@link BlacklistPort} 刻意分开：那两个的对端是本项目自己的
 * gate-txn-pay-server 与 blacklist-server（内部 rpc 方向），本端口的对端是**外部支付网关**。
 * 两类 adapter 的异常策略不同 —— 内部 rpc 方向吞异常翻 {@code Unreachable}，
 * 本方向**不吞异常、让它穿出端口**（{@code PayCenterClient.callPayCenter} 自己已经把
 * {@code IOException} 与非 2xx 吞成 {@code null}，端口层再吞一层等于两处沉默）。</p>
 *
 * <p>入参刻意仍是 {@code Map<String, Object>} 的 bizData：那些 map 的键名是供方契约
 * （{@code cardIssueCode} / {@code channelAgreementNo} / {@code refundOrderNo} …），
 * 装配逻辑散在三个服务里且各不相同，本批次只做「出网判读」的收口、不动装配。
 * <b>把 map 换成三个 DTO 属批次 4 的事，NEVER 在这里顺手做</b>。</p>
 *
 * <p><b>NEVER 把 closeResultNotify / blacklistNotify 加进本端口</b>：它们的成功判据与这三条不同，
 * 已收口在 {@code PaymentNotifyAdapter}，合进来会逼出一个「大而全的出网门面」。</p>
 */
public interface PayCenterPort {

    /** 支付宝出行扣费申请（网关 §requestPay）。 */
    PayCenterReply requestPay(Map<String, Object> bizData);

    /** 支付结果查询（网关 §payQuery）。 */
    PayCenterReply payQuery(Map<String, Object> bizData);

    /** 退款申请（网关 §requestRefund）。 */
    PayCenterReply requestRefund(Map<String, Object> bizData);

    /**
     * 退款结果查询（网关 §3.2 refundQuery）。
     *
     * <p>入参 MUST 同时带 {@code refundOrderNo} 与 {@code merchantRefundNo}（ADR-D92 实测：
     * 只送前者时网关返 {@code code=9999「退款流水号或商户退款流水号必填」}），装配在
     * {@code RefundQueryCompensationService}。<b>NEVER 在本端口内补键</b> —— 端口只做出网判读。
     */
    PayCenterReply refundQuery(Map<String, Object> bizData);
}
