package com.chinasofti.huateng.ticket.notify;

import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;
import com.chinasofti.huateng.ticket.entity.QRCodeStatus;

/**
 * APP 通知服务。
 */
public interface AppNotifyService {

    /**
     * 异步推送交易通知给 APP。
     *
     * @param request      闸机交易通知
     * @param qrCodeStatus 推进后的票卡状态
     * @param gateCardType 闸机上送的原始卡类型（未被 {@code applyActualCardType} 用 account 开户卡种覆盖前的值）。
     *                     码体票种位 MUST 与 IF8A-03（fep-app-server 路径）口径一致：取上游上送值，不做 account 覆盖。
     */
    void notifyVerifyResult(NotifyVerifyResultReqDTO request, QRCodeStatus qrCodeStatus, String gateCardType);

    /**
     * 异步推送行程数据给支付宝。
     *
     * <p>2026-09-14 review 改为异步（与 notifyVerifyResult 对齐），同时删除从未读取的 qrCodeStatus / detail 两个参数。
     *
     * @param request 闸机交易通知
     */
    void pushAlipayTripData(NotifyVerifyResultReqDTO request);
}
