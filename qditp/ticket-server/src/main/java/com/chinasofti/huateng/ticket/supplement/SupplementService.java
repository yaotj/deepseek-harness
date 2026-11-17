package com.chinasofti.huateng.ticket.supplement;

import com.chinasofti.huateng.model.app.RequestExcessFareReqDTO;
import com.chinasofti.huateng.model.app.RequestExcessFareResult;
import com.chinasofti.huateng.model.ticket.RequestCardDataAnalyseReqDTO;
import com.chinasofti.huateng.model.ticket.RequestCardDataAnalyseRespDTO;
import com.chinasofti.huateng.model.ticket.RequestCardDataUpdateReqDTO;
import com.chinasofti.huateng.model.ticket.RequestCardDataUpdateRespDTO;

public interface SupplementService {

    RequestExcessFareResult requestExcessFare(RequestExcessFareReqDTO request);

    RequestCardDataAnalyseRespDTO requestCardDataAnalyse(RequestCardDataAnalyseReqDTO request);

    RequestCardDataUpdateRespDTO requestCardDataUpdate(RequestCardDataUpdateReqDTO request);
}
