package com.chinasofti.huateng.paysign.service.impl;

import com.chinasofti.huateng.model.app.PaySignCallbackResult;
import com.chinasofti.huateng.model.app.ReceivePayResultReqDTO;
import com.chinasofti.huateng.model.app.ReceiveSignResultReqDTO;
import com.chinasofti.huateng.paysign.model.request.ReceiveTerminationResultReqDTO;
import com.chinasofti.huateng.paysign.model.response.BaseRespDTO;
import com.chinasofti.huateng.paysign.service.CallbackDomainService;
import org.springframework.stereotype.Service;

/**
 * 回调领域适配器。
 *
 * <p>回调处理与同步请求隔离，后续可在这里增加验签、幂等锁和事件投递，
 * 而无需影响签约、支付接口的调用路径。</p>
 */
@Service
public class CallbackDomainServiceImpl implements CallbackDomainService {
    private final PaySignWorkflow workflow;

    public CallbackDomainServiceImpl(PaySignWorkflow workflow) {
        this.workflow = workflow;
    }

    @Override
    public PaySignCallbackResult receiveSignResult(ReceiveSignResultReqDTO request, String signChannel) {
        return workflow.receiveSignResult(request, signChannel);
    }

    @Override
    public PaySignCallbackResult receivePayResult(ReceivePayResultReqDTO request, String rawBody) {
        return workflow.receivePayResult(request, rawBody);
    }

    @Override
    public BaseRespDTO receiveTerminationResult(ReceiveTerminationResultReqDTO request, String signChannel) {
        return workflow.receiveTerminationResult(request, signChannel);
    }
}
