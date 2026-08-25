package com.chinasofti.huateng.fep.app.service;

import com.chinasofti.huateng.model.app.PaySignCallbackResult;
import com.chinasofti.huateng.model.app.QueryPayTxnBatchReqDTO;
import com.chinasofti.huateng.model.app.ReceivePayResultReqDTO;
import com.chinasofti.huateng.model.app.ReceiveSignResultReqDTO;
import com.chinasofti.huateng.model.app.ReceiveTerminationResultReqDTO;
import com.chinasofti.huateng.model.app.RequestContractResultReqDTO;
import com.chinasofti.huateng.model.app.RequestContractResultResult;
import com.chinasofti.huateng.model.app.RequestPayReqDTO;
import com.chinasofti.huateng.model.app.RequestPayResult;
import com.chinasofti.huateng.model.app.RequestPayTxnBatchResult;
import com.chinasofti.huateng.model.app.RequestSignInfoReqDTO;
import com.chinasofti.huateng.model.app.RequestSignInfoResult;
import com.chinasofti.huateng.model.app.RequestTerminationReqDTO;
import com.chinasofti.huateng.model.app.RequestTerminationResult;

public interface PaySignAppService {
    RequestSignInfoResult requestSignInfo(RequestSignInfoReqDTO request);

    RequestTerminationResult requestTermination(RequestTerminationReqDTO request);

    RequestContractResultResult requestContractResult(RequestContractResultReqDTO request);

    RequestPayResult requestPay(RequestPayReqDTO request);

    PaySignCallbackResult receiveSignResult(ReceiveSignResultReqDTO request);

    PaySignCallbackResult receivePayResult(ReceivePayResultReqDTO request);

    PaySignCallbackResult receiveTerminationResult(ReceiveTerminationResultReqDTO request);

    RequestPayTxnBatchResult queryPayTxnBatch(QueryPayTxnBatchReqDTO request);
}
