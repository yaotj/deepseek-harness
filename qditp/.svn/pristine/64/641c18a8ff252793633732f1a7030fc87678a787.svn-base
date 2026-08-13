package com.chinasofti.huateng.fep.app.service;

import com.chinasofti.huateng.model.app.PaySignCallbackResult;
import com.chinasofti.huateng.model.app.ReceivePayResultReqDTO;
import com.chinasofti.huateng.model.app.ReceiveSignResultReqDTO;
import com.chinasofti.huateng.model.app.ReceiveTerminationResultReqDTO;
import com.chinasofti.huateng.model.app.RequestContractResultReqDTO;
import com.chinasofti.huateng.model.app.RequestContractResultResult;
import com.chinasofti.huateng.model.app.RequestSignInfoReqDTO;
import com.chinasofti.huateng.model.app.RequestSignInfoResult;
import com.chinasofti.huateng.model.app.RequestTerminationReqDTO;
import com.chinasofti.huateng.model.app.RequestTerminationResult;

public interface PaySignAppService {
    RequestSignInfoResult requestSignInfo(RequestSignInfoReqDTO request);

    RequestTerminationResult requestTermination(RequestTerminationReqDTO request);

    RequestContractResultResult requestContractResult(RequestContractResultReqDTO request);

    PaySignCallbackResult receiveSignResult(ReceiveSignResultReqDTO request);

    PaySignCallbackResult receivePayResult(ReceivePayResultReqDTO request);

    PaySignCallbackResult receiveTerminationResult(ReceiveTerminationResultReqDTO request);
}
