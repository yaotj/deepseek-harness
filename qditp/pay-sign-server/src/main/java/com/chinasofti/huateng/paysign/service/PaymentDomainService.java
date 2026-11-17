package com.chinasofti.huateng.paysign.service;

import com.chinasofti.huateng.model.app.PaySignCallbackResult;
import com.chinasofti.huateng.model.app.ReceivePayResultReqDTO;
import com.chinasofti.huateng.model.app.RequestPayReqDTO;
import com.chinasofti.huateng.model.app.RequestPayResult;
import com.chinasofti.huateng.model.paysign.RegisterCompletedPayTxnReqDTO;
import com.chinasofti.huateng.paysign.model.response.BaseRespDTO;

public interface PaymentDomainService {
    RequestPayResult requestPay(RequestPayReqDTO request);

    PaySignCallbackResult receivePayResult(ReceivePayResultReqDTO request, String rawBody);


    BaseRespDTO registerCompletedTxn(RegisterCompletedPayTxnReqDTO request);
}
