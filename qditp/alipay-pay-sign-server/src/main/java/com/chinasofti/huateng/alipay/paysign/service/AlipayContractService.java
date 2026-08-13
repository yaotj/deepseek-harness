package com.chinasofti.huateng.alipay.paysign.service;

import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripAddContractReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripTerminateContractReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripAddContractRespDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripTerminateContractRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfoDTO;
import com.chinasofti.huateng.common.response.AlipayCommonResponse;

public interface AlipayContractService {
    AlipayTripAddContractRespDTO addContract(AlipayTripAddContractReqDTO request);
    AlipayTripTerminateContractRespDTO terminateContract(AlipayTripTerminateContractReqDTO request);
    AlipaySignInfoDTO selectSignInfo(String thirdUserId);
    AlipayCommonResponse executeTermination(String agreementCode);
}
