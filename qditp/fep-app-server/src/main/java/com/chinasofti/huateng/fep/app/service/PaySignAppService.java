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
import com.chinasofti.huateng.model.app.UnbindAgreementReqDTO;
import com.chinasofti.huateng.model.app.UnbindAgreementResult;

public interface PaySignAppService {
    RequestSignInfoResult requestSignInfo(RequestSignInfoReqDTO request);

    RequestTerminationResult requestTermination(RequestTerminationReqDTO request);

    /** IF8A-75 直接解绑支付方式：立即向支付渠道发起解绑，不等账期结束的定时任务。 */
    UnbindAgreementResult unbindAgreement(UnbindAgreementReqDTO request);

    RequestContractResultResult requestContractResult(RequestContractResultReqDTO request);

    RequestPayResult requestPay(RequestPayReqDTO request);

    PaySignCallbackResult receiveSignResult(ReceiveSignResultReqDTO request);

    PaySignCallbackResult receivePayResult(ReceivePayResultReqDTO request);

    PaySignCallbackResult receiveTerminationResult(ReceiveTerminationResultReqDTO request);

    RequestPayTxnBatchResult queryPayTxnBatch(QueryPayTxnBatchReqDTO request);
}
