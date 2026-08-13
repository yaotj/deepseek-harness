package com.chinasofti.huateng.paysign.service.impl;

import com.chinasofti.huateng.model.app.RequestPayReqDTO;
import com.chinasofti.huateng.model.app.RequestPayResult;
import com.chinasofti.huateng.model.app.RequestRefundReqDTO;
import com.chinasofti.huateng.model.app.RequestRefundResult;
import com.chinasofti.huateng.paysign.service.PaymentDomainService;
import org.springframework.stereotype.Service;

/**
 * 支付领域适配器。
 *
 * <p>把支付和退款的依赖限制在此边界中。迁移期间维持既有工作流，
 * 后续可独立拆分订单持久化、退款校验和网关调用策略。</p>
 */
@Service
public class PaymentDomainServiceImpl implements PaymentDomainService {
    private final PaySignWorkflow workflow;

    public PaymentDomainServiceImpl(PaySignWorkflow workflow) {
        this.workflow = workflow;
    }

    @Override
    public RequestPayResult requestPay(RequestPayReqDTO request) {
        return workflow.requestPay(request);
    }

    @Override
    public RequestRefundResult requestRefund(RequestRefundReqDTO request) {
        return workflow.requestRefund(request);
    }
}
