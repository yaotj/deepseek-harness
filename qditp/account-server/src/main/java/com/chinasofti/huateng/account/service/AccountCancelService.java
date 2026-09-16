package com.chinasofti.huateng.account.service;

import com.chinasofti.huateng.model.app.UserCancelReqDTO;
import com.chinasofti.huateng.model.app.UserCancelResult;

/**
 * IF8A-42 用户销户。
 *
 * <p>2026-09-11 第六轮拆分：原 {@code AccountApplicationService} 是「销户 + 查询 + HCE + 两个转发」
 * 的杂物间，六个方法讲四件不相干的事，已整体删除并按概念拆开。本接口只管销户。</p>
 *
 * <p><b>NEVER 往本接口加查询、资料维护或支付通道方法</b>：那三类分别在
 * {@link AccountProfileService} 与 {@link PayChannelService}。</p>
 */
public interface AccountCancelService {

    /**
     * IF8A-42 用户销户：把该用户全部有效开户记录置为已注销。
     *
     * <p>APP 顺序是 IF8A-35 → IF8A-42 → IF8A-75，本接口执行时支付渠道尚未解绑，
     * 因此只改 {@code USER_ITP_REG_INFO} 与写 {@code USER_ITP_REG_LOG}，
     * <b>不删 {@code USER_PAY_CHANNEL}</b>。落库前会调 IF8A-35 校验未结清订单
     * （可用 {@code app.user-cancel.check-unsettled} 关闭）。</p>
     *
     * <p>已注销用户重复调用返回 {@code 0000}（幂等），<b>NEVER</b> 返回 8004。</p>
     *
     * <p><b>不校验「进行中行程」</b>——用户 2026-09-11 裁决，见 ADR-D20。</p>
     */
    UserCancelResult userCancel(UserCancelReqDTO request);
}
