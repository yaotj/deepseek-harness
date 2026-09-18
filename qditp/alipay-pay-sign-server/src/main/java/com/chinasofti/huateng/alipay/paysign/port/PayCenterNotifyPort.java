package com.chinasofti.huateng.alipay.paysign.port;

import com.chinasofti.huateng.alipay.paysign.model.response.PayCenterResponse;

import java.util.Map;

/**
 * 支付中心（bestonepay）**通知方向**出网端口 —— 黑名单变更通知、业务关闭（销卡）结果通知。
 *
 * <p><b>为什么不并进 {@link PayCenterPort}</b>：那个端口的三条（支付 / 退款 / 支付查询）返回
 * sealed {@code PayCenterReply}、按「受理 / 业务拒绝 / 无应答」三分；而这两条通知的成功判据
 * 各不相同（黑名单变更认 {@code retCode=0000} / {@code success=true} / {@code code=200} 三者任一，
 * 销卡结果只认 {@code code=200}）。合成一个端口会逼出「大而全的出网门面」—— 那正是
 * {@link PayCenterPort} 类注释里那条 NEVER 要拦的东西。拆成两个端口是对该条的**遵守**，不是绕过。
 *
 * <p><b>本端口刻意返回原始 {@link PayCenterResponse}，NEVER 在这里做成功判定</b>：判据留在
 * {@code notify.PaymentNotifyAdapter} 内（ADR-D131 的原话是「已收口在 PaymentNotifyAdapter」）。
 * 端口只承担「把 bizData 送出去、把应答带回来」这一件事，作用是让 service 层不再直接依赖
 * {@code util.PayCenterClient}、并给出可替换的接缝。
 */
public interface PayCenterNotifyPort {

    /** 黑名单状态变更通知（明文请求体）。 */
    PayCenterResponse blacklistNotify(Map<String, Object> bizData);

    /** 业务关闭（销卡）结果通知（明文请求体）。 */
    PayCenterResponse closeResultNotify(Map<String, Object> bizData);
}
