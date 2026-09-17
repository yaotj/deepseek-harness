package com.chinasofti.huateng.ticket.supplement;

import com.chinasofti.huateng.model.app.RequestExcessFareReqDTO;
import com.chinasofti.huateng.model.app.RequestExcessFareResult;
import com.chinasofti.huateng.model.ticket.RequestCardDataAnalyseReqDTO;
import com.chinasofti.huateng.model.ticket.RequestCardDataAnalyseRespDTO;
import com.chinasofti.huateng.model.ticket.RequestCardDataUpdateReqDTO;
import com.chinasofti.huateng.model.ticket.RequestCardDataUpdateRespDTO;

/** 补站域门面：{@code supplement/} 包对外的唯一入口。 */
public interface SupplementService {

    /** IF8A-04 请求自助补站。 */
    RequestExcessFareResult requestExcessFare(RequestExcessFareReqDTO request);

    /** IF5A-01 请求票卡分析。 */
    RequestCardDataAnalyseRespDTO requestCardDataAnalyse(RequestCardDataAnalyseReqDTO request);

    /** IF5A-03 请求票卡更新。 */
    RequestCardDataUpdateRespDTO requestCardDataUpdate(RequestCardDataUpdateReqDTO request);
}
