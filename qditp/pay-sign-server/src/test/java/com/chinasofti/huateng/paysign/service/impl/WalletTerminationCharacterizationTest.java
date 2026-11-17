package com.chinasofti.huateng.paysign.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chinasofti.huateng.model.app.RequestTerminationReqDTO;
import com.chinasofti.huateng.paysign.constant.PaySignErrorCodeEnum;
import com.chinasofti.huateng.paysign.model.response.RequestTerminationRespDTO;
import com.chinasofti.huateng.rpc.outcome.RpcOutcome;
import org.junit.jupiter.api.Test;

class WalletTerminationCharacterizationTest {

    private static final String WALLET_VENDOR = "0B";
    private static final String USER = "U-TEST-0004";
    private static final String SEQ = "0052290701523996";
    private static final String CARD_ID = "CARD-4";
    private static final String CARD_TYPE = "0441";

    @Test
    void walletReleaseSuccessCallsAccountDomainWithCanonicalWalletCode() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.accountDomainPort.agreeRelease(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new RpcOutcome.Ok());

        RequestTerminationRespDTO result = fixture.service.requestTermination(walletTermination(), "01");

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        verify(fixture.accountDomainPort).agreeRelease(USER, WALLET_VENDOR, CARD_ID, CARD_TYPE);
    }

    @Test
    void walletReleaseNeverCreatesTerminationRequestNorCallsGateway() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.accountDomainPort.agreeRelease(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new RpcOutcome.Ok());

        fixture.service.requestTermination(walletTermination(), "01");

        verify(fixture.terminationRequestMapper, never()).insert(any());
        verify(fixture.payGatewayClient, never()).request(anyString(), anyMap());
    }

    @Test
    void walletReleaseBizRejectedPassesRemoteMessageThrough() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.accountDomainPort.agreeRelease(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new RpcOutcome.BizRejected("8004", "该卡不存在钱包绑定"));

        RequestTerminationRespDTO result = fixture.service.requestTermination(walletTermination(), "01");

        assertEquals(PaySignErrorCodeEnum.SYSTEM_ERROR.getCode(), result.getRetCode());
        assertEquals("该卡不存在钱包绑定", result.getRetMsg());
    }

    @Test
    void walletReleaseBizRejectedWithoutMessageFallsBackToGenericText() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.accountDomainPort.agreeRelease(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new RpcOutcome.BizRejected(null, null));

        RequestTerminationRespDTO result = fixture.service.requestTermination(walletTermination(), "01");

        assertEquals(PaySignErrorCodeEnum.SYSTEM_ERROR.getCode(), result.getRetCode());
        assertEquals("钱包解绑失败", result.getRetMsg());
    }

    @Test
    void walletReleaseUnreachableIsReportedAsFailureNotSuccess() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.accountDomainPort.agreeRelease(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new RpcOutcome.Unreachable(new RuntimeException("connect timed out")));

        RequestTerminationRespDTO result = fixture.service.requestTermination(walletTermination(), "01");

        assertEquals(PaySignErrorCodeEnum.SYSTEM_ERROR.getCode(), result.getRetCode());
        assertEquals("钱包解绑失败", result.getRetMsg());
    }

    @Test
    void walletReleaseWithoutCardTypeIsRejectedLocally() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        RequestTerminationReqDTO request = walletTermination();
        request.setCardType(null);

        RequestTerminationRespDTO result = fixture.service.requestTermination(request, "01");

        assertEquals(PaySignErrorCodeEnum.INVALID_PARAM.getCode(), result.getRetCode());
        verify(fixture.accountDomainPort, never())
                .agreeRelease(anyString(), anyString(), anyString(), anyString());
    }

    private RequestTerminationReqDTO walletTermination() {
        RequestTerminationReqDTO request = new RequestTerminationReqDTO();
        request.setThirdUserId(USER);
        request.setRequestSignSeq(SEQ);
        request.setPaymentVendor(WALLET_VENDOR);
        request.setCardId(CARD_ID);
        request.setCardType(CARD_TYPE);
        return request;
    }
}
