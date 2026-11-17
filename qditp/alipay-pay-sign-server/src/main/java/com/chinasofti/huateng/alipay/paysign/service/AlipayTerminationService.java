package com.chinasofti.huateng.alipay.paysign.service;

import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripTerminateContractReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripTerminateContractRespDTO;
import com.chinasofti.huateng.common.response.AlipayCommonResponse;


public interface AlipayTerminationService {

    AlipayTripTerminateContractRespDTO terminateContract(AlipayTripTerminateContractReqDTO request);


    AlipayCommonResponse executeTermination(String agreementCode);
}
