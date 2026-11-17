package com.chinasofti.huateng.paysign.service;

import com.chinasofti.huateng.model.app.ReceiveSignResultReqDTO;
import com.chinasofti.huateng.model.paysign.CompensateNotifyRespDTO;
import com.chinasofti.huateng.model.paysign.ResendSignNotifyRespDTO;
import com.chinasofti.huateng.paysign.entity.PaySignInfo;
import com.chinasofti.huateng.paysign.entity.PaySignRequest;


public interface SignNotifyService {

    void asyncNotifySignResult(PaySignRequest request, PaySignInfo signInfo, ReceiveSignResultReqDTO receiveRequest);

    CompensateNotifyRespDTO compensateSignNotify();

    ResendSignNotifyRespDTO resendSignNotify(String requestSignSeq);
}
