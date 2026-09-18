package com.chinasofti.huateng.alipay.paysign.service;

import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripPayQueryReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripRequestPayReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripRequestRefundReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripTerminateContractReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayBlackListNotifyReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripPayNotifyReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRefundNotifyReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripCloseResultReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripRequestPayRespDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripRequestRefundRespDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripPayQueryRespDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripTerminateContractRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailRespDTO;
import com.chinasofti.huateng.common.response.AlipayCommonResponse;

public interface AlipayTripPaymentService {
    AlipayTripRequestPayRespDTO requestPay(AlipayTripRequestPayReqDTO request);
    AlipayTripRequestRefundRespDTO requestRefund(AlipayTripRequestRefundReqDTO request);
    AlipayTripPayQueryRespDTO payQuery(AlipayTripPayQueryReqDTO request);
    AlipayCommonResponse handlePayNotify(AlipayTripPayNotifyReqDTO request);
    AlipayCommonResponse handleRefundNotify(AlipayTripRefundNotifyReqDTO request);
    AlipayTripFindTravelDetailRespDTO findTravelDetail(AlipayTripFindTravelDetailReqDTO request);
    AlipayCommonResponse notifyBlackListChange(AlipayBlackListNotifyReqDTO request);
    AlipayCommonResponse notifyCloseResult(String agreementCode, boolean result);
}
