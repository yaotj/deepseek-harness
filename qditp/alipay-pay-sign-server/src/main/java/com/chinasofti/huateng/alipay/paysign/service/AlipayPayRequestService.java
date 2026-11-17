package com.chinasofti.huateng.alipay.paysign.service;

import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripRequestPayReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripRequestPayRespDTO;


public interface AlipayPayRequestService {


    AlipayTripRequestPayRespDTO requestPay(AlipayTripRequestPayReqDTO request);
}
