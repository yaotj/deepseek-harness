package com.chinasofti.huateng.alipay.paysign.service;

import com.chinasofti.huateng.model.alipaytrip.AlipayProcessTerminationReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayProcessTerminationRespDTO;


public interface AlipayTerminationInternalService {


    AlipayProcessTerminationRespDTO processTermination(AlipayProcessTerminationReqDTO request);
}
