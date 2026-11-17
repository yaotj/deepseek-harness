package com.chinasofti.huateng.paysign.service;

import com.chinasofti.huateng.model.app.ReceiveTerminationResultReqDTO;
import com.chinasofti.huateng.paysign.entity.AppTerminationRequest;
import com.chinasofti.huateng.paysign.entity.PaySignInfo;
import com.chinasofti.huateng.paysign.model.request.NotifyTerminationFailedReqDTO;


public interface TerminationNotifyService {
    void asyncNotifyTerminationResult(AppTerminationRequest terminationRequest, PaySignInfo signInfo,
                                      ReceiveTerminationResultReqDTO receiveRequest);

    void asyncNotifyTerminationFailed(AppTerminationRequest terminationRequest,
                                      NotifyTerminationFailedReqDTO receiveRequest);

    void asyncRetryTerminationNotify(AppTerminationRequest terminationRequest);
}
