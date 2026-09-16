package com.chinasofti.huateng.paysign.service;

import com.chinasofti.huateng.model.app.PaySignCallbackResult;
import com.chinasofti.huateng.model.app.ReceiveSignResultReqDTO;
import com.chinasofti.huateng.model.app.ReceiveTerminationResultReqDTO;
import com.chinasofti.huateng.paysign.model.response.BaseRespDTO;

/**
 * 支付平台异步回调领域入口，统一承接签约与解约的最终态回写。
 *
 * <p><b>支付结果回调（{@code receivePayResult}）不在本接口</b>：它的真实现随支付组搬进了
 * {@code PaymentDomainServiceImpl}，{@code PaySignServiceImpl} 直接路由到
 * {@code PaymentDomainService}。<b>NEVER 在这里加回一层转发</b> —— 那会让回调组反向依赖支付组。</p>
 */
public interface CallbackDomainService {
    PaySignCallbackResult receiveSignResult(ReceiveSignResultReqDTO request, String signChannel);

    BaseRespDTO receiveTerminationResult(ReceiveTerminationResultReqDTO request, String signChannel);
}
