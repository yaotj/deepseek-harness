package com.chinasofti.huateng.account.service;

import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestApplicationReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestApplicationRespDTO;

/**
 * 支付宝出行渠道的开卡申请（规范 3.69）。
 */
public interface AlipayTripRegistrationService {
    /**
     * 支付宝出行-开卡申请。
     *
     * @param request 支付宝出行开卡申请参数
     * @return 开卡申请结果
     */
    AlipayTripRequestApplicationRespDTO alipayTripRequestApplication(AlipayTripRequestApplicationReqDTO request);
}
