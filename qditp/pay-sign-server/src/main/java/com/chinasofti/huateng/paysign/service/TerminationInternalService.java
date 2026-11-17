package com.chinasofti.huateng.paysign.service;

import com.chinasofti.huateng.paysign.model.request.CheckFailedOrdersReqDTO;
import com.chinasofti.huateng.paysign.model.request.ExecuteTerminationReqDTO;
import com.chinasofti.huateng.paysign.model.request.NotifyTerminationFailedReqDTO;
import com.chinasofti.huateng.paysign.model.response.BaseRespDTO;
import com.chinasofti.huateng.paysign.model.response.CheckFailedOrdersRespDTO;
import com.chinasofti.huateng.model.paysign.ProcessTerminationReqDTO;
import com.chinasofti.huateng.model.paysign.ProcessTerminationRespDTO;
import com.chinasofti.huateng.model.app.UnbindAgreementReqDTO;
import com.chinasofti.huateng.model.app.UnbindAgreementResult;
import com.chinasofti.huateng.model.paysign.CompensateNotifyRespDTO;

public interface TerminationInternalService {


    ProcessTerminationRespDTO processTermination(ProcessTerminationReqDTO request);

    CompensateNotifyRespDTO compensateTerminationNotify();

    CompensateNotifyRespDTO compensateChannelSync();

    CheckFailedOrdersRespDTO checkFailedOrders(CheckFailedOrdersReqDTO request);

    BaseRespDTO executeTermination(ExecuteTerminationReqDTO request);


    UnbindAgreementResult unbindAgreement(UnbindAgreementReqDTO request);

    BaseRespDTO notifyTerminationFailed(NotifyTerminationFailedReqDTO request);
}
