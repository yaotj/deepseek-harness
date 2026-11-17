package com.chinasofti.huateng.alipay.paysign.service;

import com.chinasofti.huateng.common.response.AlipayCommonResponse;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripPayNotifyReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRefundNotifyReqDTO;


public interface AlipayPayCallbackService {


    AlipayCommonResponse handlePayNotify(AlipayTripPayNotifyReqDTO request);


    AlipayCommonResponse handleRefundNotify(AlipayTripRefundNotifyReqDTO request);
}
