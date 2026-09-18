package com.chinasofti.huateng.ticket.notify;

import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;
import com.chinasofti.huateng.ticket.entity.QRCodeStatus;

/** APP 通知服务。 */
public interface AppNotifyService {

    /**
     * 异步推送交易通知给 APP。
     *
     * @param request 闸机交易通知
     * @param qrCodeStatus 推进后的票卡状态
     * @param gateCardType 闸机上送的原始卡类型（未被 {@code applyActualCardType} 用 account 开户卡种覆盖前的值）。
     */
    void notifyVerifyResult(NotifyVerifyResultReqDTO request, QRCodeStatus qrCodeStatus, String gateCardType);

    /**
     * 异步推送行程数据给支付宝。
     *
     * @param request 闸机交易通知
     */
    void pushAlipayTripData(NotifyVerifyResultReqDTO request);

    /**
     * 异步推送多日票次数扣减通知给 APP（甲方规格 R6 §3.63）。
     *
     * <p>**MUST 在 daily-ticket 确认扣次成功之后才调用** —— 扣次被拒或 RPC 技术失败时不通知，
     * 否则造成「APP 以为扣了、实际没扣」。唯一调用点是
     * {@code GateDailyTicketCoordinator.markUsedOnExit}。
     *
     * @param request 闸机交易通知（{@code transSeq} 取其 {@code ticketTransSeq}）
     * @param times 本次扣减次数，当前恒为 1（见 AGENTS.md §2.2.2 该条裁决）
     */
    void notifyCountingTicketTimes(NotifyVerifyResultReqDTO request, int times);
}
