package com.chinasofti.huateng.alipay.paysign.service;

import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripAddContractReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripPayQueryReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripRequestPayReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripRequestRefundReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripTerminateContractReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripAddContractRespDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripPayQueryRespDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripRequestPayRespDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripRequestRefundRespDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripTerminateContractRespDTO;
import com.chinasofti.huateng.alipay.paysign.entity.AlipayPayLog;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripPayNotifyReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfoDTO;

import java.util.List;

/**
 * 支付宝签约与支付服务接口。
 */
public interface AlipayPaySignService {
    AlipayTripAddContractRespDTO addContract(AlipayTripAddContractReqDTO request);
    AlipayTripTerminateContractRespDTO terminateContract(AlipayTripTerminateContractReqDTO request);
    AlipayTripRequestPayRespDTO requestPay(AlipayTripRequestPayReqDTO request);
    AlipayTripRequestRefundRespDTO requestRefund(AlipayTripRequestRefundReqDTO request);
    AlipayTripPayQueryRespDTO payQuery(AlipayTripPayQueryReqDTO request);

    /**
     * 处理支付宝支付结果回调。
     */
    boolean handlePayNotify(AlipayTripPayNotifyReqDTO request);

    /**
     * 查询用户签约信息。
     */
    AlipaySignInfoDTO selectSignInfo(String thirdUserId);

    /**
     * 查询支付宝出行订单列表。
     */
    List<AlipayPayLog> selectAlipayPayLogList(String thirdUserId, String cardId, String payStatus, String startTime, String endTime, int offset, int limit);

    /**
     * 查询支付宝出行订单总数。
     */
    int countAlipayPayLogList(String thirdUserId, String cardId, String payStatus, String startTime, String endTime);
}
