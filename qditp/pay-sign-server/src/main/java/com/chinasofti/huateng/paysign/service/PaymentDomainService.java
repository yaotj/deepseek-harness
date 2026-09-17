package com.chinasofti.huateng.paysign.service;

import com.chinasofti.huateng.model.app.PaySignCallbackResult;
import com.chinasofti.huateng.model.app.ReceivePayResultReqDTO;
import com.chinasofti.huateng.model.app.RequestPayReqDTO;
import com.chinasofti.huateng.model.app.RequestPayResult;

/** 支付领域入口，负责支付订单的同步受理与支付结果回调收口。 */
public interface PaymentDomainService {
    RequestPayResult requestPay(RequestPayReqDTO request);

    /** 支付 API 5.1 支付结果回调。 */
    PaySignCallbackResult receivePayResult(ReceivePayResultReqDTO request, String rawBody);
}
