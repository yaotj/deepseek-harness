package com.chinasofti.huateng.paysign.service;

import com.chinasofti.huateng.model.app.PaySignCallbackResult;
import com.chinasofti.huateng.model.app.ReceiveSignResultReqDTO;
import com.chinasofti.huateng.model.app.ReceiveTerminationResultReqDTO;
import com.chinasofti.huateng.paysign.model.response.BaseRespDTO;

public interface CallbackDomainService {
    PaySignCallbackResult receiveSignResult(ReceiveSignResultReqDTO request, String signChannel);

    BaseRespDTO receiveTerminationResult(ReceiveTerminationResultReqDTO request, String signChannel);
}
