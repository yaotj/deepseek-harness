package com.chinasofti.huateng.paysign.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chinasofti.huateng.model.app.RequestContractResultReqDTO;
import com.chinasofti.huateng.paysign.entity.PaySignInfo;
import com.chinasofti.huateng.paysign.model.response.PaySignGatewayResponse;
import com.chinasofti.huateng.paysign.model.response.RequestContractResultRespDTO;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;


class ContractGatewayStatusApplyTest {

    private static final String SEQ = "0052290701523999";
    private static final String USER = "U-TEST-0001";
    private static final String ALIPAY_VENDOR = "03";
    private static final String METRO_APP = "01";

    @Test
    void sameStatusOnlyPatchesIdsAndNeverMigrates() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.paySignInfoMapper.selectBySeq(SEQ, null)).thenReturn(localRecord("SIGNED"));
        fixture.gatewayReturns(gatewayData("SIGNED"));

        RequestContractResultRespDTO result = fixture.service.requestContractResult(query(), METRO_APP);

        assertEquals("PA-9", result.getPayAccountId());
        assertEquals("AGR-9", result.getPayAgreementNo());
        verify(fixture.paySignInfoMapper).updateBySeq(any(PaySignInfo.class));
        verifyNoStatusMigration(fixture);
    }


    @Test
    void missingStatusInGatewayDataOnlyPatchesIds() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.paySignInfoMapper.selectBySeq(SEQ, null)).thenReturn(localRecord(null));
        PaySignGatewayResponse response = PaySignFacadeFixture.gatewaySuccess();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("payUserId", "PA-9");
        data.put("payAgreementNo", "AGR-9");
        response.setData(data);
        fixture.gatewayReturns(response);

        fixture.service.requestContractResult(query(), METRO_APP);

        verify(fixture.paySignInfoMapper).updateBySeq(any(PaySignInfo.class));
        verifyNoStatusMigration(fixture);
    }

    @Test
    void unsignedFromGatewayGoesThroughMarkUnsigned() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.paySignInfoMapper.selectBySeq(SEQ, null)).thenReturn(localRecord("SIGNED"));
        fixture.gatewayReturns(gatewayData("UNSIGNED"));
        when(fixture.paySignInfoMapper.markUnsigned(anyString(), any())).thenReturn(1);

        RequestContractResultRespDTO result = fixture.service.requestContractResult(query(), METRO_APP);

        assertEquals("UNSIGNED", result.getStatus());
        verify(fixture.paySignInfoMapper).markUnsigned(anyString(), any());
        verify(fixture.paySignInfoMapper, never()).markSigned(anyString(), anyString(), anyString(), any());
        verify(fixture.paySignInfoMapper, never()).updateBySeq(any(PaySignInfo.class));
    }

    @Test
    void notSignedFromGatewayGoesThroughReactivateForResign() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.paySignInfoMapper.selectBySeq(SEQ, null)).thenReturn(localRecord("UNSIGNED"));
        fixture.gatewayReturns(gatewayData("NOT_SIGNED"));
        when(fixture.paySignInfoMapper.reactivateForResign(SEQ)).thenReturn(1);

        RequestContractResultRespDTO result = fixture.service.requestContractResult(query(), METRO_APP);

        assertEquals("NOT_SIGNED", result.getStatus());
        verify(fixture.paySignInfoMapper).reactivateForResign(SEQ);
        verify(fixture.paySignInfoMapper, never()).markUnsigned(anyString(), any());
    }


    @Test
    void unknownGatewayStatusIsNeverPersisted() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.paySignInfoMapper.selectBySeq(SEQ, null)).thenReturn(localRecord("SIGNED"));
        fixture.gatewayReturns(gatewayData("PROCESSING_NEW_CODE"));

        RequestContractResultRespDTO result = fixture.service.requestContractResult(query(), METRO_APP);

        assertEquals("PROCESSING_NEW_CODE", result.getStatus(), "未知状态 MUST 原样返回，便于对账时发现契约漂移");
        verify(fixture.paySignInfoMapper, never()).updateBySeq(any(PaySignInfo.class));
        verifyNoStatusMigration(fixture);
    }


    @Test
    void casMissWithWhitelistConflictAnswersObservedStatus() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.paySignInfoMapper.selectBySeq(SEQ, null)).thenReturn(localRecord("SIGNED"));
        fixture.gatewayReturns(gatewayData("UNSIGNED"));
        when(fixture.paySignInfoMapper.markUnsigned(anyString(), any())).thenReturn(0);
        when(fixture.paySignInfoMapper.selectSignStatusBySeq(SEQ)).thenReturn("SIGN_FAILED");

        RequestContractResultRespDTO result = fixture.service.requestContractResult(query(), METRO_APP);

        assertEquals("SIGN_FAILED", result.getStatus(), "CAS 落空 MUST 答库里的真实状态");
    }

    @Test
    void casMissAlreadyAtTargetIsTreatedAsIdempotent() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.paySignInfoMapper.selectBySeq(SEQ, null)).thenReturn(localRecord("SIGNED"));
        fixture.gatewayReturns(gatewayData("UNSIGNED"));
        when(fixture.paySignInfoMapper.markUnsigned(anyString(), any())).thenReturn(0);
        when(fixture.paySignInfoMapper.selectSignStatusBySeq(SEQ)).thenReturn("UNSIGNED");

        RequestContractResultRespDTO result = fixture.service.requestContractResult(query(), METRO_APP);

        assertEquals("UNSIGNED", result.getStatus());
    }

    private void verifyNoStatusMigration(PaySignFacadeFixture fixture) {
        verify(fixture.paySignInfoMapper, never()).markSigned(anyString(), anyString(), anyString(), any());
        verify(fixture.paySignInfoMapper, never()).markUnsigned(anyString(), any());
        verify(fixture.paySignInfoMapper, never()).reactivateForResign(anyString());
    }

    private RequestContractResultReqDTO query() {
        RequestContractResultReqDTO request = new RequestContractResultReqDTO();
        request.setThirdUserId(USER);
        request.setRequestSignSeq(SEQ);
        request.setPaymentVendor(ALIPAY_VENDOR);
        return request;
    }

    private PaySignInfo localRecord(String contractStatus) {
        PaySignInfo signInfo = new PaySignInfo();
        signInfo.setRequestSignSeq(SEQ);
        signInfo.setThirdUserId(USER);
        signInfo.setPaymentVendor(ALIPAY_VENDOR);
        signInfo.setContractStatus(contractStatus);
        return signInfo;
    }

    private PaySignGatewayResponse gatewayData(String status) {
        PaySignGatewayResponse response = PaySignFacadeFixture.gatewaySuccess();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", status);
        data.put("payUserId", "PA-9");
        data.put("payAgreementNo", "AGR-9");
        response.setData(data);
        return response;
    }
}
