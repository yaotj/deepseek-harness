package com.chinasofti.huateng.paysign.service;

import com.chinasofti.huateng.model.app.PaySignCallbackResult;
import com.chinasofti.huateng.model.app.ReceiveRefundResultReqDTO;
import com.chinasofti.huateng.model.app.RequestRefundReqDTO;
import com.chinasofti.huateng.model.app.RequestRefundResult;
import com.chinasofti.huateng.model.paysign.CompensateNotifyRespDTO;
import com.chinasofti.huateng.paysign.model.response.BaseRespDTO;

public interface RefundDomainService {

    RequestRefundResult requestRefund(RequestRefundReqDTO request);


    PaySignCallbackResult receiveRefundResult(ReceiveRefundResultReqDTO request);


    BaseRespDTO queryRefundResult(String refundOrderNo);

    CompensateNotifyRespDTO compensateRefundQuery();

    CompensateNotifyRespDTO compensateRefundSummary();
}
