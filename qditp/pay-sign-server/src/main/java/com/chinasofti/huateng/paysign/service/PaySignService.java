package com.chinasofti.huateng.paysign.service;

import com.chinasofti.huateng.model.app.PaySignCallbackResult;
import com.chinasofti.huateng.model.app.ReceiveSignResultReqDTO;
import com.chinasofti.huateng.model.app.ReceivePayResultReqDTO;
import com.chinasofti.huateng.model.app.ReceiveRefundResultReqDTO;
import com.chinasofti.huateng.model.app.RequestPayReqDTO;
import com.chinasofti.huateng.model.app.RequestPayResult;
import com.chinasofti.huateng.model.app.RequestRefundReqDTO;
import com.chinasofti.huateng.model.app.RequestRefundResult;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripAddContractReqDTO;
import com.chinasofti.huateng.model.app.RequestAgreeReleaseReqDTO;
import com.chinasofti.huateng.model.app.RequestAgreeReleaseResult;
import com.chinasofti.huateng.model.app.RequestSignInfoReqDTO;
import com.chinasofti.huateng.model.app.RequestSignInfoResult;
import com.chinasofti.huateng.model.paysign.PaySignInfoDTO;
import com.chinasofti.huateng.model.app.ReceiveTerminationResultReqDTO;
import com.chinasofti.huateng.paysign.model.request.RequestContractAdvisoryReqDTO;
import com.chinasofti.huateng.model.app.RequestContractResultReqDTO;
import com.chinasofti.huateng.model.app.RequestTerminationReqDTO;
import com.chinasofti.huateng.paysign.model.response.BaseRespDTO;
import com.chinasofti.huateng.paysign.model.response.RequestContractAdvisoryRespDTO;
import com.chinasofti.huateng.paysign.model.response.RequestContractResultRespDTO;
import com.chinasofti.huateng.paysign.model.response.RequestTerminationRespDTO;

public interface PaySignService {
    RequestSignInfoResult requestSignInfo(RequestSignInfoReqDTO request, String signChannel);

    RequestSignInfoResult alipayTripRequestSignInfo(AlipayTripAddContractReqDTO request);

    RequestContractAdvisoryRespDTO requestContractAdvisory(RequestContractAdvisoryReqDTO request, String signChannel);

    RequestContractResultRespDTO requestContractResult(RequestContractResultReqDTO request, String signChannel);

    RequestTerminationRespDTO requestTermination(RequestTerminationReqDTO request, String signChannel);

    RequestAgreeReleaseResult removeSignAgreement(RequestAgreeReleaseReqDTO request, String signChannel);

    RequestPayResult requestPay(RequestPayReqDTO request);

    RequestRefundResult requestRefund(RequestRefundReqDTO request);

    PaySignCallbackResult receiveSignResult(ReceiveSignResultReqDTO request, String signChannel);

    PaySignCallbackResult receivePayResult(ReceivePayResultReqDTO request, String rawBody);

    PaySignCallbackResult receiveRefundResult(ReceiveRefundResultReqDTO request);

    BaseRespDTO receiveTerminationResult(ReceiveTerminationResultReqDTO request, String signChannel);

    PaySignInfoDTO querySignInfoBySeq(String requestSignSeq);


    boolean updateDisplayAccountByThirdUserId(String thirdUserId, String displayAccount);


    com.chinasofti.huateng.model.app.RequestPayTxnBatchResult queryPayTxnBatch(com.chinasofti.huateng.model.app.QueryPayTxnBatchReqDTO request);
}
