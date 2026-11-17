package com.chinasofti.huateng.fep.alipay.service;

import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestRefundReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestRefundRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripPayQueryReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripPayQueryRespDTO;
import com.chinasofti.huateng.model.app.AddBlackListReqDTO;
import com.chinasofti.huateng.model.app.BlackListOperateResult;


public interface AlipayPaymentService {


    AlipayTripRequestRefundRespDTO requestRefund(AlipayTripRequestRefundReqDTO request);


    BlackListOperateResult addBlackListForAlipay(AddBlackListReqDTO request);


    com.chinasofti.huateng.model.alipaytrip.TerminationExecuteResult executeTermination(String agreementCode);
}
