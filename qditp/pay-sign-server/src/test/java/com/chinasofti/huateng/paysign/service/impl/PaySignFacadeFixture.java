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

final class PaySignFacadeFixture {

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

    final PaySignAuditLogger auditLogger;

    final ContractDomainServiceImpl contractDomainService;

    final PaymentDomainServiceImpl paymentDomainService;

    final RefundDomainServiceImpl refundDomainService;

    final CallbackDomainServiceImpl callbackDomainService;

    final PaySignGateway paySignGateway = new PaySignGateway(payGatewayClient);

    final ContractGatewayPort contractGatewayPort = new ContractGatewayAdapter(properties, paySignGateway);

    final PaymentGatewayPort paymentGatewayPort = new PaymentGatewayAdapter(properties, paySignGateway);

    final RefundGatewayPort refundGatewayPort = new RefundGatewayAdapter(properties, paySignGateway);

    final DebitSyncPort debitSyncPort = new DebitSyncRpcAdapter(gateTxnPayClient);

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
                payTxnDetailMapper,
                payCallbackLogMapper);
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

    void gatewayReturns(PaySignGatewayResponse response) {
        when(payGatewayClient.request(anyString(), anyMap())).thenReturn(response);
    }

    static PaySignGatewayResponse gatewaySuccess() {
        PaySignGatewayResponse response = new PaySignGatewayResponse();
        response.setCode(0);
        response.setMsg("成功");
        return response;
    }

    static PaySignGatewayResponse gatewayFailure(int code, String msg) {
        PaySignGatewayResponse response = new PaySignGatewayResponse();
        response.setCode(code);
        response.setMsg(msg);
        return response;
    }

    List<PaySignRequest> auditLogs() {
        ArgumentCaptor<PaySignRequest> captor = ArgumentCaptor.forClass(PaySignRequest.class);
        org.mockito.Mockito.verify(paySignRequestMapper, org.mockito.Mockito.atLeast(0)).insert(captor.capture());
        return captor.getAllValues();
    }
}
