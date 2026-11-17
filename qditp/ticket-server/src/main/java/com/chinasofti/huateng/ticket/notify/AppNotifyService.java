package com.chinasofti.huateng.ticket.notify;

import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;
import com.chinasofti.huateng.ticket.entity.QRCodeStatus;

public interface AppNotifyService {


    void notifyVerifyResult(NotifyVerifyResultReqDTO request, QRCodeStatus qrCodeStatus, String gateCardType);


    void pushAlipayTripData(NotifyVerifyResultReqDTO request);


    void notifyCountingTicketTimes(NotifyVerifyResultReqDTO request, int times);
}
