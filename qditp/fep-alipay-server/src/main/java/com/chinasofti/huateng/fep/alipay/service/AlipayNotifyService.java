package com.chinasofti.huateng.fep.alipay.service;

import com.chinasofti.huateng.model.alipaytrip.AlipayTripPayNotifyReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripPayNotifyRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripCloseResultReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripCloseResultRespDTO;


public interface AlipayNotifyService {


    AlipayTripPayNotifyRespDTO handlePaymentNotify(AlipayTripPayNotifyReqDTO request);


    AlipayTripCloseResultRespDTO closeResult(AlipayTripCloseResultReqDTO request);
}
