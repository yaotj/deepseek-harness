package com.chinasofti.huateng.paysign.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chinasofti.huateng.model.app.UnbindAgreementReqDTO;
import com.chinasofti.huateng.model.app.UnbindAgreementResult;
import com.chinasofti.huateng.paysign.constant.PaySignErrorCodeEnum;
import com.chinasofti.huateng.paysign.entity.AppTerminationRequest;
import com.chinasofti.huateng.paysign.entity.PaySignInfo;
import com.chinasofti.huateng.paysign.model.response.PaySignGatewayResponse;
import com.chinasofti.huateng.paysign.port.GatewayReply;
import com.chinasofti.huateng.paysign.port.AccountPayChannelView;
import com.chinasofti.huateng.paysign.port.AccountQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TerminationInternalReadCharacterizationTest {

    private static final String SEQ = "0052290701523999";
    private static final String USER = "U-TEST-0001";
    private static final String ALIPAY_VENDOR = "03";
    private static final String ACCOUNT_CARD_ID = "CARD-FROM-ACCOUNT";
    private static final String ACCOUNT_CARD_TYPE = "0441";
    private static final String BIZ_REJECT_CODE = "8004";

    @Test
    @DisplayName("IF8A-75 直接解绑：账户域答成功时用它的 CARD_ID/CARD_TYPE 补建解约申请")
    void unbindAgreementFillsCardInfoFromAccount() {
        TerminationInternalFixture fixture = givenUnbindReady();
        when(fixture.accountDomainPort.queryPayChannelByContract(anyString()))
                .thenReturn(found(ACCOUNT_CARD_ID, ACCOUNT_CARD_TYPE));

        UnbindAgreementResult result = fixture.service.unbindAgreement(unbindRequest());

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        AppTerminationRequest inserted = captureInserted(fixture);
        assertEquals(ACCOUNT_CARD_ID, inserted.getCardId());
        assertEquals(ACCOUNT_CARD_TYPE, inserted.getCardType());
        assertEquals("PENDING", inserted.getTerminationStatus());
    }

    @Test
    @DisplayName("IF8A-75 直接解绑：账户域业务拒绝时不中断、不采信，两列回落签约记录")
    void unbindAgreementDoesNotFillCardInfoOnAccountBizRejection() {
        TerminationInternalFixture fixture = givenUnbindReady();
        when(fixture.accountDomainPort.queryPayChannelByContract(anyString())).thenReturn(notFound());

        UnbindAgreementResult result = fixture.service.unbindAgreement(unbindRequest());

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        AppTerminationRequest inserted = captureInserted(fixture);
        assertNull(inserted.getCardId());
        assertNull(inserted.getCardType());
    }

    @Test
    @DisplayName("IF8A-75 直接解绑：账户域不可达时不中断")
    void unbindAgreementSurvivesAccountUnreachable() {
        TerminationInternalFixture fixture = givenUnbindReady();
        when(fixture.accountDomainPort.queryPayChannelByContract(anyString())).thenReturn(unreachable());

        UnbindAgreementResult result = fixture.service.unbindAgreement(unbindRequest());

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        assertNull(captureInserted(fixture).getCardId());
    }

    @Test
    @DisplayName("IF8A-75 直接解绑：端口抛异常时不中断，异常不外溢")
    void unbindAgreementSurvivesPortThrowing() {
        TerminationInternalFixture fixture = givenUnbindReady();
        when(fixture.accountDomainPort.queryPayChannelByContract(anyString()))
                .thenThrow(new RuntimeException("connect timed out"));

        UnbindAgreementResult result = fixture.service.unbindAgreement(unbindRequest());

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        assertNull(captureInserted(fixture).getCardId());
    }

    @Test
    @DisplayName("IF8A-75 直接解绑：钱包渠道短路成 8001，不查账户域")
    void unbindAgreementShortCircuitsWalletVendor() {
        TerminationInternalFixture fixture = TerminationInternalFixture.create();

        UnbindAgreementReqDTO request = unbindRequest();
        request.setPaymentVendor("0B");
        UnbindAgreementResult result = fixture.service.unbindAgreement(request);

        assertEquals(PaySignErrorCodeEnum.INVALID_PARAM.getCode(), result.getRetCode());
        verify(fixture.accountDomainPort, org.mockito.Mockito.never()).queryPayChannelByContract(anyString());
    }

    private TerminationInternalFixture givenUnbindReady() {
        TerminationInternalFixture fixture = TerminationInternalFixture.create();
        when(fixture.terminationRequestMapper.selectByRequestSignSeq(SEQ)).thenReturn(null);
        PaySignInfo signInfo = new PaySignInfo();
        signInfo.setThirdUserId(USER);
        signInfo.setPaymentVendor(ALIPAY_VENDOR);
        when(fixture.paySignInfoMapper.selectBySeq(SEQ, ALIPAY_VENDOR)).thenReturn(signInfo);
        when(fixture.terminationRequestMapper.markScanning(anyString(), any())).thenReturn(1);
        PaySignGatewayResponse gateway = new PaySignGatewayResponse();
        gateway.setCode(0);
        gateway.setMsg("成功");
        when(fixture.contractGatewayPort.requestDismissal(SEQ)).thenReturn(new GatewayReply.Accepted(gateway));
        return fixture;
    }

    private AppTerminationRequest captureInserted(TerminationInternalFixture fixture) {
        ArgumentCaptor<AppTerminationRequest> captor = ArgumentCaptor.forClass(AppTerminationRequest.class);
        verify(fixture.terminationRequestMapper).insert(captor.capture());
        return captor.getValue();
    }

    private AccountQuery<AccountPayChannelView> found(String cardId, String cardType) {
        return new AccountQuery.Found<>(new AccountPayChannelView(cardId, cardType, USER));
    }

    private AccountQuery<AccountPayChannelView> notFound() {
        return new AccountQuery.NotFound<>(BIZ_REJECT_CODE, "该用户无此支付通道");
    }

    private AccountQuery<AccountPayChannelView> unreachable() {
        return new AccountQuery.Unreachable<>(new RuntimeException("connect timed out"));
    }

    private UnbindAgreementReqDTO unbindRequest() {
        UnbindAgreementReqDTO request = new UnbindAgreementReqDTO();
        request.setThirdUserId(USER);
        request.setPaymentVendor(ALIPAY_VENDOR);
        request.setRequestSignSeq(SEQ);
        return request;
    }
}
