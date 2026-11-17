package com.chinasofti.huateng.paysign.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chinasofti.huateng.model.app.RequestSignInfoReqDTO;
import com.chinasofti.huateng.model.app.RequestSignInfoResult;
import com.chinasofti.huateng.paysign.constant.PaySignErrorCodeEnum;
import com.chinasofti.huateng.paysign.entity.AppTerminationRequest;
import com.chinasofti.huateng.paysign.entity.PaySignInfo;
import com.chinasofti.huateng.paysign.entity.PaySignRequest;
import com.chinasofti.huateng.paysign.model.request.RequestContractAdvisoryReqDTO;
import com.chinasofti.huateng.model.app.RequestTerminationReqDTO;
import com.chinasofti.huateng.paysign.model.response.PaySignGatewayResponse;
import com.chinasofti.huateng.paysign.model.response.RequestContractAdvisoryRespDTO;
import com.chinasofti.huateng.paysign.model.response.RequestTerminationRespDTO;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ContractDomainCharacterizationTest {

    private static final String WALLET_VENDOR = "0B";
    private static final String ALIPAY_VENDOR = "03";
    private static final String SEQ = "0052290701523999";
    private static final String USER = "U-TEST-0001";

    @Test
    void walletRequestSignInfoReachesGatewayLikeOtherVendors() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        PaySignGatewayResponse gateway = PaySignFacadeFixture.gatewaySuccess();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("data", "wallet://contract?fake=1");
        gateway.setData(data);
        fixture.gatewayReturns(gateway);

        RequestSignInfoResult result = fixture.service.requestSignInfo(signInfoRequest(WALLET_VENDOR), "01");

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        assertEquals("wallet://contract?fake=1", result.getRequestStartSdkInfo());
        verify(fixture.payGatewayClient).request(anyString(), anyMap());
    }

    @Test
    void requestSignInfoRejectsAlreadySignedUser() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.paySignInfoMapper.selectByUserAndVendor(USER, ALIPAY_VENDOR)).thenReturn(new PaySignInfo());

        RequestSignInfoResult result = fixture.service.requestSignInfo(signInfoRequest(ALIPAY_VENDOR), "01");

        assertEquals(PaySignErrorCodeEnum.ALREADY_SIGNED.getCode(), result.getRetCode());
        verify(fixture.payGatewayClient, never()).request(anyString(), anyMap());
    }

    @Test
    void requestSignInfoReturnsSdkInfoFromGatewayData() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        PaySignGatewayResponse gateway = PaySignFacadeFixture.gatewaySuccess();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("data", "alipays://platformapi/startapp?fake=1");
        gateway.setData(data);
        fixture.gatewayReturns(gateway);

        RequestSignInfoResult result = fixture.service.requestSignInfo(signInfoRequest(ALIPAY_VENDOR), "01");

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        assertEquals("alipays://platformapi/startapp?fake=1", result.getRequestStartSdkInfo());
    }

    @Test
    void requestSignInfoMapsGatewayFailureToSystemError() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        fixture.gatewayReturns(PaySignFacadeFixture.gatewayFailure(600, "操作失败"));

        RequestSignInfoResult result = fixture.service.requestSignInfo(signInfoRequest(ALIPAY_VENDOR), "01");

        assertEquals(PaySignErrorCodeEnum.SYSTEM_ERROR.getCode(), result.getRetCode());
    }

    @Test
    void signSideAuditLogOperationTypeIsAlwaysSign() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        fixture.gatewayReturns(PaySignFacadeFixture.gatewayFailure(600, "操作失败"));

        fixture.service.requestSignInfo(signInfoRequest(ALIPAY_VENDOR), "01");

        List<PaySignRequest> logs = fixture.auditLogs();
        assertTrue(logs.size() >= 1, "requestSignInfo MUST 落审计流水");
        assertEquals("SIGN", logs.get(logs.size() - 1).getOperationType());
    }

    @Test
    void walletContractAdvisoryIsAnsweredSuccessfullyWithoutGateway() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();

        RequestContractAdvisoryRespDTO result = fixture.service.requestContractAdvisory(advisoryRequest(WALLET_VENDOR), "01");

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        verify(fixture.payGatewayClient, never()).request(anyString(), anyMap());
    }

    @Test
    void contractAdvisoryUsesConfiguredAdvisoryUrl() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        fixture.gatewayReturns(PaySignFacadeFixture.gatewaySuccess());

        fixture.service.requestContractAdvisory(advisoryRequest(ALIPAY_VENDOR), "01");

        ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
        verify(fixture.payGatewayClient).request(url.capture(), anyMap());
        assertEquals(PaySignFacadeFixture.GATEWAY_URL_PREFIX + "creditQuery", url.getValue());
    }

    @Test
    void contractAdvisoryMapsGatewayFailureToSystemError() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        fixture.gatewayReturns(PaySignFacadeFixture.gatewayFailure(600, "操作失败"));

        RequestContractAdvisoryRespDTO result = fixture.service.requestContractAdvisory(advisoryRequest(ALIPAY_VENDOR), "01");

        assertEquals(PaySignErrorCodeEnum.SYSTEM_ERROR.getCode(), result.getRetCode());
    }

    @Test
    void requestTerminationRejectsUnsignedUserAndLogsUnsignOperation() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.paySignInfoMapper.selectBySeq(SEQ, ALIPAY_VENDOR)).thenReturn(null);

        RequestTerminationRespDTO result = fixture.service.requestTermination(terminationRequest(), "01");

        assertEquals(PaySignErrorCodeEnum.USER_NOT_SIGNED.getCode(), result.getRetCode());
        List<PaySignRequest> logs = fixture.auditLogs();
        assertEquals("UNSIGN", logs.get(logs.size() - 1).getOperationType());
    }

    @Test
    void requestTerminationInsertsPendingRequestAndSkipsPayPlatform() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        PaySignInfo signInfo = new PaySignInfo();
        signInfo.setCardId("CARD-1");
        signInfo.setCardType("0441");
        signInfo.setPaymentVendor(ALIPAY_VENDOR);
        when(fixture.paySignInfoMapper.selectBySeq(SEQ, ALIPAY_VENDOR)).thenReturn(signInfo);

        RequestTerminationRespDTO result = fixture.service.requestTermination(terminationRequest(), "01");

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        assertEquals(SEQ, result.getRequestSignSeq());
        ArgumentCaptor<AppTerminationRequest> captor = ArgumentCaptor.forClass(AppTerminationRequest.class);
        verify(fixture.terminationRequestMapper).insert(captor.capture());
        AppTerminationRequest inserted = captor.getValue();
        assertEquals("PENDING", inserted.getTerminationStatus());
        assertEquals("PENDING", inserted.getNotifyStatus());
        assertEquals(Integer.valueOf(0), inserted.getNotifyRetryCount());
        assertNotNull(inserted.getRequestTime());
        verify(fixture.payGatewayClient, never()).request(anyString(), anyMap());
    }

    @Test
    void requestTerminationRejectsWhenSeqAlreadyHasNonFailedRequest() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.paySignInfoMapper.selectBySeq(SEQ, ALIPAY_VENDOR)).thenReturn(new PaySignInfo());
        AppTerminationRequest existing = new AppTerminationRequest();
        existing.setTerminationStatus("SUCCESS");
        when(fixture.terminationRequestMapper.selectByRequestSignSeq(SEQ)).thenReturn(existing);

        RequestTerminationRespDTO result = fixture.service.requestTermination(terminationRequest(), "01");

        assertEquals(PaySignErrorCodeEnum.ALREADY_TERMINATING.getCode(), result.getRetCode());
        verify(fixture.terminationRequestMapper, never()).insert(any());
    }

    private RequestSignInfoReqDTO signInfoRequest(String payChannelCode) {
        RequestSignInfoReqDTO request = new RequestSignInfoReqDTO();
        request.setThirdUserId(USER);
        request.setDisplayAccount("138****8000");
        request.setPayChannelCode(payChannelCode);
        request.setRequestSignSeq(SEQ);
        return request;
    }

    private RequestContractAdvisoryReqDTO advisoryRequest(String paymentVendor) {
        RequestContractAdvisoryReqDTO request = new RequestContractAdvisoryReqDTO();
        request.setThirdUserId(USER);
        request.setRequestSignSeq(SEQ);
        request.setPaymentVendor(paymentVendor);
        return request;
    }

    private RequestTerminationReqDTO terminationRequest() {
        RequestTerminationReqDTO request = new RequestTerminationReqDTO();
        request.setThirdUserId(USER);
        request.setRequestSignSeq(SEQ);
        request.setPaymentVendor(ALIPAY_VENDOR);
        return request;
    }
}
