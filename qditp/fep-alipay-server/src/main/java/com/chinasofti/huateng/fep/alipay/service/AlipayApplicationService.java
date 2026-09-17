package com.chinasofti.huateng.fep.alipay.service;

import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestApplicationReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestApplicationRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestIndustryDataReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestIndustryDataRespDTO;

/**
 * 支付宝出行开卡/行业数据服务接口。
 */
public interface AlipayApplicationService {

    /**
     * 开卡申请。
     * @param request 请求对象
     * @return 响应对象
     */
    AlipayTripRequestApplicationRespDTO requestApplication(AlipayTripRequestApplicationReqDTO request);

    /**
     * 获取行业数据。
     * @param request 请求对象
     * @return 响应对象
     */
    AlipayTripRequestIndustryDataRespDTO requestIndustryData(AlipayTripRequestIndustryDataReqDTO request);
}
