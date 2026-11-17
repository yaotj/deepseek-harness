package com.chinasofti.huateng.paysign.port;

import com.chinasofti.huateng.model.app.RequestPayReqDTO;

public interface PaymentGatewayPort {

    PaymentReply requestPay(RequestPayReqDTO request);

    GatewayReply queryPayStatus(String merchantOrderNo);
}
