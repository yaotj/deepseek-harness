package com.chinasofti.huateng.paysign.service;

import com.chinasofti.huateng.model.alipaytrip.AlipayTripAddContractReqDTO;
import com.chinasofti.huateng.model.app.RequestAgreeReleaseReqDTO;
import com.chinasofti.huateng.model.app.RequestAgreeReleaseResult;
import com.chinasofti.huateng.model.app.RequestSignInfoReqDTO;
import com.chinasofti.huateng.model.app.RequestSignInfoResult;
import com.chinasofti.huateng.paysign.model.request.RequestContractAdvisoryReqDTO;
import com.chinasofti.huateng.model.app.RequestContractResultReqDTO;
import com.chinasofti.huateng.model.app.RequestTerminationReqDTO;
import com.chinasofti.huateng.paysign.model.response.RequestContractAdvisoryRespDTO;
import com.chinasofti.huateng.paysign.model.response.RequestContractResultRespDTO;
import com.chinasofti.huateng.paysign.model.response.RequestTerminationRespDTO;

/** 签约领域入口。 */
public interface ContractDomainService {
    RequestSignInfoResult requestSignInfo(RequestSignInfoReqDTO request, String signChannel);

    RequestSignInfoResult alipayTripRequestSignInfo(AlipayTripAddContractReqDTO request);

    RequestContractAdvisoryRespDTO requestContractAdvisory(RequestContractAdvisoryReqDTO request, String signChannel);

    RequestContractResultRespDTO requestContractResult(RequestContractResultReqDTO request, String signChannel);

    RequestTerminationRespDTO requestTermination(RequestTerminationReqDTO request, String signChannel);

    /** IF8A-36 请求移除签约信息。 */
    RequestAgreeReleaseResult removeSignAgreement(RequestAgreeReleaseReqDTO request, String signChannel);

    /** requestPayPlatformTermination / queryPayPlatformContractStatus 两个方法已于 2026-09-16 删除。 */
}
