package com.chinasofti.huateng.paysign.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.chinasofti.huateng.paysign.audit.PaySignAuditLogger;
import com.chinasofti.huateng.paysign.client.PayGatewayClient;
import com.chinasofti.huateng.paysign.config.PaySignProperties;
import com.chinasofti.huateng.paysign.entity.PaySignRequest;
import com.chinasofti.huateng.paysign.mapper.AppTerminationRequestMapper;
import com.chinasofti.huateng.paysign.mapper.PayCallbackLogMapper;
import com.chinasofti.huateng.paysign.mapper.PayRefundDetailMapper;
import com.chinasofti.huateng.paysign.mapper.PaySignInfoMapper;
import com.chinasofti.huateng.paysign.mapper.PaySignRequestMapper;
import com.chinasofti.huateng.paysign.mapper.PayTxnDetailMapper;
import com.chinasofti.huateng.paysign.model.response.PaySignGatewayResponse;
import com.chinasofti.huateng.paysign.port.AccountDomainPort;
import com.chinasofti.huateng.paysign.port.ContractGatewayAdapter;
import com.chinasofti.huateng.paysign.port.DebitSyncPort;
import com.chinasofti.huateng.paysign.port.DebitSyncRpcAdapter;
import com.chinasofti.huateng.paysign.port.ContractGatewayPort;
import com.chinasofti.huateng.paysign.port.PaymentGatewayAdapter;
import com.chinasofti.huateng.paysign.port.PaymentGatewayPort;
import com.chinasofti.huateng.paysign.port.RefundGatewayAdapter;
import com.chinasofti.huateng.paysign.port.RefundGatewayPort;
import com.chinasofti.huateng.paysign.service.PaySignService;
import com.chinasofti.huateng.paysign.service.TerminationNotifyService;
import com.chinasofti.huateng.paysign.support.PaySignGateway;
import com.chinasofti.huateng.rpc.blacklist.BlacklistClient;
import com.chinasofti.huateng.rpc.pay.GateTxnPayClient;
import java.util.List;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/** 测试夹具：构造器装配 + 真实现协作者，断言一律经门面下钻，才能证明拆分前后行为等价。 */
final class PaySignFacadeFixture {

    /** 网关地址只要「非空且可辨识」即可：真实调用已被 mock 掉，断言看的是传了哪个 URL。 */
    static final String GATEWAY_URL_PREFIX = "http://pay-gateway.test/api/v1/";

    final PaySignProperties properties = new PaySignProperties();
    final PaySignInfoMapper paySignInfoMapper = mock(PaySignInfoMapper.class);
    final PaySignRequestMapper paySignRequestMapper = mock(PaySignRequestMapper.class);
    final PayTxnDetailMapper payTxnDetailMapper = mock(PayTxnDetailMapper.class);
    final AppTerminationRequestMapper terminationRequestMapper = mock(AppTerminationRequestMapper.class);
    final PayRefundDetailMapper payRefundDetailMapper = mock(PayRefundDetailMapper.class);
    final PayCallbackLogMapper payCallbackLogMapper = mock(PayCallbackLogMapper.class);
    final TerminationNotifyService terminationNotifyService = mock(TerminationNotifyService.class);
    final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    final AccountDomainPort accountDomainPort = mock(AccountDomainPort.class);
    final PayGatewayClient payGatewayClient = mock(PayGatewayClient.class);
    final BlacklistClient blacklistClient = mock(BlacklistClient.class);
    final GateTxnPayClient gateTxnPayClient = mock(GateTxnPayClient.class);
    final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
    final ChannelSyncDeliverer channelSyncDeliverer = mock(ChannelSyncDeliverer.class);

    /** 审计流水写入点用**真实实现**，不 mock。 */
    final PaySignAuditLogger auditLogger;

    /** 签约 + 解约组（{@code requestSignInfo} / {@code alipayTripRequestSignInfo} /。 */
    final ContractDomainServiceImpl contractDomainService;

    /** 支付组（{@code requestPay} / {@code requestRefund} / {@code receivePayResult}）的真实现。 */
    final PaymentDomainServiceImpl paymentDomainService;

    /** 退款组（{@code requestRefund} / {@code compensateRefundQuery} / {@code compensateRefundSummary}）的真实现。 */
    final RefundDomainServiceImpl refundDomainService;

    /** 回调组（{@code receiveSignResult} / {@code receiveTerminationResult}）的真实现。 */
    final CallbackDomainServiceImpl callbackDomainService;

    /** 网关调用与应答判读的收口 Bean，内部包着同一个 {@link #payGatewayClient} mock。 */
    final PaySignGateway paySignGateway = new PaySignGateway(payGatewayClient);

    /** 签约/解约方向的出向端口，用真实现（2026-09-16，ADR-D112）。 */
    final ContractGatewayPort contractGatewayPort = new ContractGatewayAdapter(properties, paySignGateway);

    /** 扣款方向的出向端口，同样用真实现、同样包着那两个 mock（理由同上）。 */
    final PaymentGatewayPort paymentGatewayPort = new PaymentGatewayAdapter(properties, paySignGateway);

    /** 退款方向的出向端口，同样用真实现（理由同上）。 */
    final RefundGatewayPort refundGatewayPort = new RefundGatewayAdapter(properties, paySignGateway);

    /** 闸机域扣费状态收敛方向的出向端口，用真实现、包着同一个 gateTxnPayClient mock（2026-09-17，ADR-D119）。 */
    final DebitSyncPort debitSyncPort = new DebitSyncRpcAdapter(gateTxnPayClient);

    /** 与生产完全同构的调用链门面：{@code PaySignServiceImpl} → 三个领域服务。 */
    final PaySignService service;

    private PaySignFacadeFixture() {
        fillGatewayUrls();
        auditLogger = new PaySignAuditLogger(paySignRequestMapper);
        contractDomainService = new ContractDomainServiceImpl(
                paySignInfoMapper,
                terminationRequestMapper,
                auditLogger,
                accountDomainPort,
                contractGatewayPort);
        paymentDomainService = new PaymentDomainServiceImpl(
                properties,
                payTxnDetailMapper,
                payCallbackLogMapper,
                accountDomainPort,
                blacklistClient,
                debitSyncPort,
                paymentGatewayPort);
        refundDomainService = new RefundDomainServiceImpl(
                payTxnDetailMapper,
                payRefundDetailMapper,
                refundGatewayPort);
        callbackDomainService = new CallbackDomainServiceImpl(
                new SignResultCallbackHandler(
                        paySignInfoMapper, paySignRequestMapper, auditLogger, eventPublisher),
                new TerminationResultCallbackHandler(
                        paySignInfoMapper, paySignRequestMapper, terminationRequestMapper,
                        terminationNotifyService, auditLogger, transactionTemplate, channelSyncDeliverer));
        stubTransactionTemplate();
        stubGatewayPureMethods();
        service = new PaySignServiceImpl(
                contractDomainService,
                paymentDomainService,
                refundDomainService,
                callbackDomainService,
                paySignInfoMapper,
                payTxnDetailMapper);
    }

    static PaySignFacadeFixture create() {
        return new PaySignFacadeFixture();
    }

    private void fillGatewayUrls() {
        properties.setContractUrl(GATEWAY_URL_PREFIX + "contract");
        properties.setContractAdvisoryUrl(GATEWAY_URL_PREFIX + "creditQuery");
        properties.setContractResultUrl(GATEWAY_URL_PREFIX + "contract/queryResult");
        properties.setTerminationUrl(GATEWAY_URL_PREFIX + "contract/dismissal");
        properties.setRequestPayUrl(GATEWAY_URL_PREFIX + "pay");
        properties.setPayQueryUrl(GATEWAY_URL_PREFIX + "pay/query");
        properties.setRequestRefundUrl(GATEWAY_URL_PREFIX + "refund");
        properties.setDefaultNotifyUrl("http://itp.test/notify");
        properties.setRequestPayNotifyUrl("http://itp.test/payNotify");
    }

    /** 让 {@code execute} 直接跑回调，等价于「本地事务成功提交」。 */
    private void stubTransactionTemplate() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
    }

    private void stubGatewayPureMethods() {
        when(payGatewayClient.isSuccess(any())).thenCallRealMethod();
        when(payGatewayClient.errorMessage(any(), anyString())).thenCallRealMethod();
    }

    /** 让网关对任意 URL 返回给定应答。 */
    void gatewayReturns(PaySignGatewayResponse response) {
        when(payGatewayClient.request(anyString(), anyMap())).thenReturn(response);
    }

    /** 网关成功应答（{@code code=0}），{@code data} 由调用方按需塞。 */
    static PaySignGatewayResponse gatewaySuccess() {
        PaySignGatewayResponse response = new PaySignGatewayResponse();
        response.setCode(0);
        response.setMsg("成功");
        return response;
    }

    /** 网关业务失败应答。 */
    static PaySignGatewayResponse gatewayFailure(int code, String msg) {
        PaySignGatewayResponse response = new PaySignGatewayResponse();
        response.setCode(code);
        response.setMsg(msg);
        return response;
    }

    /** 取本次调用写入 {@code APP_PAY_SIGN_REQUEST} 的全部审计流水。 */
    List<PaySignRequest> auditLogs() {
        ArgumentCaptor<PaySignRequest> captor = ArgumentCaptor.forClass(PaySignRequest.class);
        org.mockito.Mockito.verify(paySignRequestMapper, org.mockito.Mockito.atLeast(0)).insert(captor.capture());
        return captor.getAllValues();
    }
}
