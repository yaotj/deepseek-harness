package com.chinasofti.huateng.account.service;

import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestApplicationReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestApplicationRespDTO;


public interface AlipayTripRegistrationService {

    AlipayTripRequestApplicationRespDTO alipayTripRequestApplication(AlipayTripRequestApplicationReqDTO request);
}
