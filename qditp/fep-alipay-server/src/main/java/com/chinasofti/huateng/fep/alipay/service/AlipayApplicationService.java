package com.chinasofti.huateng.fep.alipay.service;

import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestApplicationReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestApplicationRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestIndustryDataReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestIndustryDataRespDTO;


public interface AlipayApplicationService {


    AlipayTripRequestApplicationRespDTO requestApplication(AlipayTripRequestApplicationReqDTO request);


    AlipayTripRequestIndustryDataRespDTO requestIndustryData(AlipayTripRequestIndustryDataReqDTO request);
}
