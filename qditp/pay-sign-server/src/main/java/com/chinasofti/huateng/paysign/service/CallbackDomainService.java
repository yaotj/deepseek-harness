package com.chinasofti.huateng.paysign.service;

import com.chinasofti.huateng.model.app.PaySignCallbackResult;
import com.chinasofti.huateng.model.app.ReceiveSignResultReqDTO;
import com.chinasofti.huateng.model.app.ReceiveTerminationResultReqDTO;
import com.chinasofti.huateng.paysign.model.response.BaseRespDTO;

/** 支付平台异步回调领域入口，统一承接签约与解约的最终态回写。 */
public interface CallbackDomainService {
    PaySignCallbackResult receiveSignResult(ReceiveSignResultReqDTO request, String signChannel);

    BaseRespDTO receiveTerminationResult(ReceiveTerminationResultReqDTO request, String signChannel);
}
