package com.chinasofti.huateng.paysign.service.impl;

import com.chinasofti.huateng.model.app.PaySignCallbackResult;
import com.chinasofti.huateng.model.app.ReceiveSignResultReqDTO;
import com.chinasofti.huateng.model.app.ReceiveTerminationResultReqDTO;
import com.chinasofti.huateng.paysign.model.response.BaseRespDTO;
import com.chinasofti.huateng.paysign.service.CallbackDomainService;
import org.springframework.stereotype.Service;


@Service
public class CallbackDomainServiceImpl implements CallbackDomainService {

    private final SignResultCallbackHandler signResultCallbackHandler;
    private final TerminationResultCallbackHandler terminationResultCallbackHandler;

    public CallbackDomainServiceImpl(SignResultCallbackHandler signResultCallbackHandler,
                                     TerminationResultCallbackHandler terminationResultCallbackHandler) {
        this.signResultCallbackHandler = signResultCallbackHandler;
        this.terminationResultCallbackHandler = terminationResultCallbackHandler;
    }

    @Override
    public PaySignCallbackResult receiveSignResult(ReceiveSignResultReqDTO request, String signChannel) {
        return signResultCallbackHandler.receiveSignResult(request, signChannel);
    }

    @Override
    public BaseRespDTO receiveTerminationResult(ReceiveTerminationResultReqDTO request, String signChannel) {
        return terminationResultCallbackHandler.receiveTerminationResult(request, signChannel);
    }
}
