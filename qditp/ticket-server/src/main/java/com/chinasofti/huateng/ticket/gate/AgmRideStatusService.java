package com.chinasofti.huateng.ticket.gate;

import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultRespDTO;
import com.chinasofti.huateng.model.ticket.QueryStatusReqDTO;
import com.chinasofti.huateng.model.ticket.QueryStatusRespDTO;

public interface AgmRideStatusService {


    NotifyVerifyResultRespDTO notifyVerifyResult(NotifyVerifyResultReqDTO request);


    QueryStatusRespDTO queryQrCodeStatus(QueryStatusReqDTO request);

}
