package com.chinasofti.huateng.ticket.service;

import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;
import com.chinasofti.huateng.ticket.entity.QRCodeStatus;
import com.chinasofti.huateng.ticket.entity.QRCodeTxnDetail;

/**
 * APP 通知服务。
 */
public interface AppNotifyService {

    /**
     * 异步推送交易通知给 APP。
     *
     * @param request 闸机交易通知
     */
    void notifyVerifyResult(NotifyVerifyResultReqDTO request, QRCodeStatus qrCodeStatus);

    /**
     * 异步推送行程数据给支付宝。
     *
     * @param request 闸机交易通知
     * @param qrCodeStatus 二维码状态
     * @param detail 交易明细
     */
    void pushAlipayTripData(NotifyVerifyResultReqDTO request, QRCodeStatus qrCodeStatus, QRCodeTxnDetail detail);
}
