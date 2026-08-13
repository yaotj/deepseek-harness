package com.chinasofti.huateng.paysign.service.impl;

import com.chinasofti.huateng.model.alipaytrip.AlipayTripAddContractReqDTO;
import com.chinasofti.huateng.model.app.RequestSignInfoReqDTO;
import com.chinasofti.huateng.model.app.RequestSignInfoResult;
import com.chinasofti.huateng.paysign.model.request.RequestContractAdvisoryReqDTO;
import com.chinasofti.huateng.paysign.model.request.RequestContractResultReqDTO;
import com.chinasofti.huateng.paysign.model.request.RequestTerminationReqDTO;
import com.chinasofti.huateng.paysign.model.response.RequestContractAdvisoryRespDTO;
import com.chinasofti.huateng.paysign.model.response.RequestContractResultRespDTO;
import com.chinasofti.huateng.paysign.model.response.RequestTerminationRespDTO;
import com.chinasofti.huateng.paysign.service.ContractDomainService;
import org.springframework.stereotype.Service;

/**
 * 签约领域适配器。
 *
 * <p>目前委派给既有工作流以保证迁移安全；新的签约规则应逐步沉淀到此类，
 * 直至可以移除工作流中的签约代码。</p>
 */
@Service
public class ContractDomainServiceImpl implements ContractDomainService {
    private final PaySignWorkflow workflow;

    public ContractDomainServiceImpl(PaySignWorkflow workflow) {
        this.workflow = workflow;
    }

    @Override
    public RequestSignInfoResult requestSignInfo(RequestSignInfoReqDTO request, String signChannel) {
        return workflow.requestSignInfo(request, signChannel);
    }

    @Override
    public RequestSignInfoResult alipayTripRequestSignInfo(AlipayTripAddContractReqDTO request) {
        return workflow.alipayTripRequestSignInfo(request);
    }

    @Override
    public RequestContractAdvisoryRespDTO requestContractAdvisory(RequestContractAdvisoryReqDTO request, String signChannel) {
        return workflow.requestContractAdvisory(request, signChannel);
    }

    @Override
    public RequestContractResultRespDTO requestContractResult(RequestContractResultReqDTO request, String signChannel) {
        return workflow.requestContractResult(request, signChannel);
    }

    @Override
    public RequestTerminationRespDTO requestTermination(RequestTerminationReqDTO request, String signChannel) {
        return workflow.requestTermination(request, signChannel);
    }
}
