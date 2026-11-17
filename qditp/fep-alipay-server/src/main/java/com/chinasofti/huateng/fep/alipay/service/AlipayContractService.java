package com.chinasofti.huateng.fep.alipay.service;

import com.chinasofti.huateng.model.alipaytrip.AlipayTripAddContractReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripAddContractRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripTerminateContractReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripTerminateContractRespDTO;


public interface AlipayContractService {


    AlipayTripAddContractRespDTO addContract(AlipayTripAddContractReqDTO request);


    AlipayTripTerminateContractRespDTO terminateContract(AlipayTripTerminateContractReqDTO request);
}
