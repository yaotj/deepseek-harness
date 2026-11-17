package com.chinasofti.huateng.alipay.paysign.service;

import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripAddContractReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripAddContractRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfoDTO;


public interface AlipaySignContractService {


    AlipayTripAddContractRespDTO addContract(AlipayTripAddContractReqDTO request);


    AlipaySignInfoDTO selectSignInfo(String thirdUserId);
}
