package com.chinasofti.huateng.alipay.paysign.port;

import java.util.Map;

/**
 * 支付中心（bestonepay）出网端口 —— 支付 / 退款 / 支付查询三个方向，ADR-D131。
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
}
