package com.chinasofti.huateng.paysign.service;

import com.chinasofti.huateng.model.app.PaySignCallbackResult;
import com.chinasofti.huateng.model.app.ReceiveSignResultReqDTO;
import com.chinasofti.huateng.model.app.ReceivePayResultReqDTO;
import com.chinasofti.huateng.model.app.RequestPayReqDTO;
import com.chinasofti.huateng.model.app.RequestPayResult;
import com.chinasofti.huateng.model.app.RequestRefundReqDTO;
import com.chinasofti.huateng.model.app.RequestRefundResult;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripAddContractReqDTO;
import com.chinasofti.huateng.model.app.RequestAgreeReleaseReqDTO;
import com.chinasofti.huateng.model.app.RequestAgreeReleaseResult;
import com.chinasofti.huateng.model.app.RequestSignInfoReqDTO;
import com.chinasofti.huateng.model.app.RequestSignInfoResult;
import com.chinasofti.huateng.model.paysign.PaySignInfoDTO;
import com.chinasofti.huateng.paysign.model.request.ReceiveTerminationResultReqDTO;
import com.chinasofti.huateng.paysign.model.request.RequestContractAdvisoryReqDTO;
import com.chinasofti.huateng.paysign.model.request.RequestContractResultReqDTO;
import com.chinasofti.huateng.paysign.model.request.RequestTerminationReqDTO;
import com.chinasofti.huateng.paysign.model.response.BaseRespDTO;
import com.chinasofti.huateng.paysign.model.response.RequestContractAdvisoryRespDTO;
import com.chinasofti.huateng.paysign.model.response.RequestContractResultRespDTO;
import com.chinasofti.huateng.paysign.model.response.RequestTerminationRespDTO;

public interface PaySignService {
    RequestSignInfoResult requestSignInfo(RequestSignInfoReqDTO request, String signChannel);

    /**
     * 支付宝出行-添加签约信息。
     * <p>
     * 接收支付宝 DTO，映射为内部 RequestSignInfoReqDTO，固定签约渠道为 ALIPAY，
     * 同步确认签约成功并写入 APP_PAY_SIGN_INFO 表和流水表。
     * </p>
     */
    RequestSignInfoResult alipayTripRequestSignInfo(AlipayTripAddContractReqDTO request);

    RequestContractAdvisoryRespDTO requestContractAdvisory(RequestContractAdvisoryReqDTO request, String signChannel);

    RequestContractResultRespDTO requestContractResult(RequestContractResultReqDTO request, String signChannel);

    RequestTerminationRespDTO requestTermination(RequestTerminationReqDTO request, String signChannel);

    /**
     * IF8A-36 请求移除签约信息。
     *
     * <p>与解约不同，移除签约不请求支付系统，直接更新签约记录状态为解约成功。</p>
     */
    RequestAgreeReleaseResult removeSignAgreement(RequestAgreeReleaseReqDTO request, String signChannel);

    RequestPayResult requestPay(RequestPayReqDTO request);

    RequestRefundResult requestRefund(RequestRefundReqDTO request);

    PaySignCallbackResult receiveSignResult(ReceiveSignResultReqDTO request, String signChannel);

    PaySignCallbackResult receivePayResult(ReceivePayResultReqDTO request, String rawBody);

    BaseRespDTO receiveTerminationResult(ReceiveTerminationResultReqDTO request, String signChannel);

    PaySignInfoDTO querySignInfoBySeq(String requestSignSeq);

    /**
     * 更新用户签约展示账号（如更换手机号时同步更新）。
     *
     * @param thirdUserId 第三方用户ID
     * @param displayAccount 新的展示账号
     * @return 是否更新成功
     */
    boolean updateDisplayAccountByThirdUserId(String thirdUserId, String displayAccount);

    /**
     * IF8A-05 批量查询支付明细（供 ticket-server 双源合并）。
     *
     * @param request 订单号列表请求
     * @return 支付明细结果包装
     */
    com.chinasofti.huateng.model.app.RequestPayTxnBatchResult queryPayTxnBatch(com.chinasofti.huateng.model.app.QueryPayTxnBatchReqDTO request);
}
