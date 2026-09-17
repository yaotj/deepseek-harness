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
import com.chinasofti.huateng.paysign.service.AppNotifyService;
import com.chinasofti.huateng.paysign.service.TerminationInternalService;
import com.chinasofti.huateng.paysign.support.PaySignGateway;
import com.chinasofti.huateng.rpc.pay.GateTxnPayClient;

/** 测试夹具：解约内部端点专用，断言挂在接口上，解约执行簇用真实现。 */
final class TerminationInternalFixture {

    final AppTerminationRequestMapper terminationRequestMapper = mock(AppTerminationRequestMapper.class);
    final PaySignInfoMapper paySignInfoMapper = mock(PaySignInfoMapper.class);
    final PaySignRequestMapper paySignRequestMapper = mock(PaySignRequestMapper.class);

    /** 支付中心签约/解约方向的出向端口（2026-09-16，ADR-D115）。 */
    final ContractGatewayPort contractGatewayPort = mock(ContractGatewayPort.class);
    final AppNotifyService appNotifyService = mock(AppNotifyService.class);
    /** 扫表补偿三兄弟已于 2026-09-16 拆到 {@link TerminationCompensationService}，本类只剩一行委托。 */
    final TerminationCompensationService terminationCompensationService =
            mock(TerminationCompensationService.class);
    final GateTxnPayClient gateTxnPayClient = mock(GateTxnPayClient.class);
    final AccountDomainPort accountDomainPort = mock(AccountDomainPort.class);
    final PayGatewayClient payGatewayClient = mock(PayGatewayClient.class);

    /** 与生产同构：真实 {@link PaySignGateway} 包着 mock 出去的 {@link PayGatewayClient}。 */
    final PaySignGateway paySignGateway = new PaySignGateway(payGatewayClient);

    /** 审计写入点用真实实现，与 {@link PaySignFacadeFixture} 同一个理由（口径要有测试守着）。 */
    final PaySignAuditLogger auditLogger = new PaySignAuditLogger(paySignRequestMapper);

    /** 被测对象，**以接口类型暴露**，防止测试顺手调到实现细节。 */
    final TerminationInternalService service;

    /** 解约执行簇的真实现（2026-09-16 拆出），**刻意不 mock**。 */
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
                gateTxnPayClient,
                appNotifyService);
    }

    static TerminationInternalFixture create() {
        return new TerminationInternalFixture();
    }
}
