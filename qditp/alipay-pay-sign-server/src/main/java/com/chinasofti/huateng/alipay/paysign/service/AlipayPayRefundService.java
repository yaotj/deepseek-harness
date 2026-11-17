package com.chinasofti.huateng.alipay.paysign.service;

import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripRequestRefundReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripRequestRefundRespDTO;


public interface AlipayPayRefundService {


    AlipayTripRequestRefundRespDTO requestRefund(AlipayTripRequestRefundReqDTO request);
}
