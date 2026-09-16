package com.chinasofti.huateng.paysign.service.impl;

import static org.mockito.Mockito.mock;

import com.chinasofti.huateng.paysign.audit.PaySignAuditLogger;
import com.chinasofti.huateng.paysign.client.PayGatewayClient;
import com.chinasofti.huateng.paysign.mapper.AppTerminationRequestMapper;
import com.chinasofti.huateng.paysign.mapper.PaySignInfoMapper;
import com.chinasofti.huateng.paysign.mapper.PaySignRequestMapper;
import com.chinasofti.huateng.paysign.port.AccountDomainPort;
import com.chinasofti.huateng.paysign.service.AppNotifyService;
import com.chinasofti.huateng.paysign.service.ContractDomainService;
import com.chinasofti.huateng.paysign.service.TerminationInternalService;
import com.chinasofti.huateng.paysign.support.PaySignGateway;
import com.chinasofti.huateng.rpc.pay.GateTxnPayClient;

/**
 * {@link TerminationInternalServiceImpl} 的测试装配夹具。
 *
 * <p><b>为什么与 {@link PaySignFacadeFixture} 分开</b>：那个夹具装的是「APP 入口门面 → 四个领域服务」
 * 这条链，而本类装的是**解约内部端点**（{@code /internal/termination/**}，全部由 web-admin 的
 * Quartz 触发、不经 APP 门面）。两条链的协作者集合只有 4 个重叠，硬合成一个夹具会让任一侧的
 * 依赖变动都惊动另一侧。
 *
 * <p><b>断言 MUST 挂在 {@link TerminationInternalService} 这个接口上</b>，NEVER 直接调
 * {@link TerminationInternalServiceImpl} 的私有方法。理由与 `PaySignFacadeFixture` 那条完全一致：
 * 解约簇后续要按业务能力重排，断言挂在对外接口上，代码在底下怎么搬「绿」都仍然证明行为等价；
 * 挂在实现细节上则一搬家就得改测试，那时「全绿」只证明代码和测试被一起改了。
 *
 * <p><b>刻意不起 Spring 上下文</b>：起上下文要拉 Druid 与 mybatis-adaptor，本机没有 Oracle 就跑不了，
 * 测试也就永远不会被真的执行。这里只把 mock 从构造器传进去，纯 JVM 内跑。
 *
 * <p><b>装配 MUST 走构造器，NEVER 退回 {@code ReflectionTestUtils.setField}</b>（2026-09-16，ADR-D96）：
 * 被测类的协作者已全部 {@code final} + 构造注入，于是「夹具与被测类的协作者集合不一致」
 * 由**编译器**拦住；反射注入只能等到运行时才报 {@code Could not find field}，而且**名字写错才报**，
 * 少注一个只会留下一个 {@code null} 字段、直到某条用例正好走到它才炸。
 */
final class TerminationInternalFixture {

    final AppTerminationRequestMapper terminationRequestMapper = mock(AppTerminationRequestMapper.class);
    final PaySignInfoMapper paySignInfoMapper = mock(PaySignInfoMapper.class);
    final PaySignRequestMapper paySignRequestMapper = mock(PaySignRequestMapper.class);
    final ContractDomainService contractDomainService = mock(ContractDomainService.class);
    final AppNotifyService appNotifyService = mock(AppNotifyService.class);
    /**
     * 扫表补偿三兄弟已于 2026-09-16 拆到 {@link TerminationCompensationService}，本类只剩一行委托。
     *
     * <p>因此 {@code channelSyncDeliverer} / {@code terminationProcessor} / 两个重试上限
     * <b>不再是被测类的协作者</b>，这里也不再注入 —— 它们的行为该由那个类自己的测试守。
     * <b>NEVER 因为「以前注过」把它们加回来</b>：改成构造注入后（ADR-D96）多传一个参数
     * 直接**编译不过**，比原先反射注入抛 {@code IllegalArgumentException} 更早拦住。</p>
     */
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

    /**
     * 解约执行簇的真实现（2026-09-16 拆出），**刻意不 mock**。
     *
     * <p>门面的 {@code executeTermination} / {@code unbindAgreement} 现在只剩一行委托到这里，
     * 若把它换成 mock，挂在 {@link TerminationInternalService} 上的断言就只能证明「委托调用发生了」，
     * 证不出状态机、CAS、票卡回填这些真正要守的口径 —— 那等于把特征测试变成空转。
     * 与 {@link PaySignFacadeFixture} 里 {@code auditLogger} / {@code paySignGateway} 用真实现是同一个理由：
     * <b>真实现 + 共用同一批 mock 协作者</b>，于是拆分前写的断言一条都不用改就继续通过。
     * <b>NEVER 把它换成 mock。</b></p>
     */
    final TerminationExecutor terminationExecutor;

    private TerminationInternalFixture() {
        TerminationExecutor executor = new TerminationExecutor(
                terminationRequestMapper,
                paySignInfoMapper,
                contractDomainService,
                paySignGateway,
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
