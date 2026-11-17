package com.chinasofti.huateng.paysign.service.impl;

import static org.mockito.Mockito.mock;

import com.chinasofti.huateng.paysign.audit.PaySignAuditLogger;
import com.chinasofti.huateng.paysign.client.PayGatewayClient;
import com.chinasofti.huateng.paysign.mapper.AppTerminationRequestMapper;
import com.chinasofti.huateng.paysign.mapper.PaySignInfoMapper;
import com.chinasofti.huateng.paysign.mapper.PaySignRequestMapper;
import com.chinasofti.huateng.paysign.port.AccountDomainPort;
import com.chinasofti.huateng.paysign.port.ContractGatewayPort;
import com.chinasofti.huateng.paysign.port.GatewayReply;
import com.chinasofti.huateng.paysign.service.TerminationInternalService;
import com.chinasofti.huateng.paysign.service.TerminationNotifyService;
import com.chinasofti.huateng.paysign.support.PaySignGateway;
import com.chinasofti.huateng.paysign.port.UnsettledOrderRpcAdapter;
import com.chinasofti.huateng.rpc.pay.GateTxnPayClient;

final class TerminationInternalFixture {

    final AppTerminationRequestMapper terminationRequestMapper = mock(AppTerminationRequestMapper.class);
    final PaySignInfoMapper paySignInfoMapper = mock(PaySignInfoMapper.class);
    final PaySignRequestMapper paySignRequestMapper = mock(PaySignRequestMapper.class);

    final ContractGatewayPort contractGatewayPort = mock(ContractGatewayPort.class);
    final TerminationNotifyService terminationNotifyService = mock(TerminationNotifyService.class);
    final TerminationCompensationService terminationCompensationService =
            mock(TerminationCompensationService.class);
    final GateTxnPayClient gateTxnPayClient = mock(GateTxnPayClient.class);
    final AccountDomainPort accountDomainPort = mock(AccountDomainPort.class);
    final PayGatewayClient payGatewayClient = mock(PayGatewayClient.class);

    final PaySignGateway paySignGateway = new PaySignGateway(payGatewayClient);

    final PaySignAuditLogger auditLogger = new PaySignAuditLogger(paySignRequestMapper);

    final TerminationInternalService service;

    final TerminationExecutor terminationExecutor;

    private TerminationInternalFixture() {
        TerminationExecutor executor = new TerminationExecutor(
                terminationRequestMapper,
                paySignInfoMapper,
                contractGatewayPort,
                auditLogger,
                accountDomainPort);
        this.terminationExecutor = executor;

        this.service = new TerminationInternalServiceImpl(
                terminationCompensationService,
                executor,
                terminationRequestMapper,
                new UnsettledOrderRpcAdapter(gateTxnPayClient),
                terminationNotifyService);
    }

    static TerminationInternalFixture create() {
        return new TerminationInternalFixture();
    }
}
