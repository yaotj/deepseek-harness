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
}
