package com.chinasofti.huateng.paysign.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chinasofti.huateng.model.app.RequestContractResultReqDTO;
import com.chinasofti.huateng.paysign.constant.PaySignErrorCodeEnum;
import com.chinasofti.huateng.paysign.entity.PaySignInfo;
import com.chinasofti.huateng.paysign.model.response.PaySignGatewayResponse;
import com.chinasofti.huateng.paysign.model.response.RequestContractResultRespDTO;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ContractResultCharacterizationTest {

    private static final String ALIPAY_VENDOR = "03";
    private static final String SEQ = "0052290701523999";
    private static final String USER = "U-TEST-0001";

    @Test
    void localSignedRecordShortCircuitsWithoutGateway() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.paySignInfoMapper.selectBySeq(SEQ, null)).thenReturn(signedLocalRecord());

        RequestContractResultRespDTO result = fixture.service.requestContractResult(query(), "01");

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        assertEquals("SIGNED", result.getStatus());
        assertEquals("PA-1", result.getPayAccountId());
        assertEquals("PA-1", result.getPayUserId());
        assertEquals("AGR-1", result.getPayAgreementNo());
        verify(fixture.payGatewayClient, never()).request(anyString(), anyMap());
    }

    @Test
    void ownershipMismatchIsAnsweredAsRecordNotExistAndNeverReachesGateway() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        PaySignInfo other = signedLocalRecord();
        other.setThirdUserId("U-SOMEONE-ELSE");
        when(fixture.paySignInfoMapper.selectBySeq(SEQ, null)).thenReturn(other);

        RequestContractResultRespDTO result = fixture.service.requestContractResult(query(), "01");

        assertEquals(PaySignErrorCodeEnum.RECORD_NOT_EXIST.getCode(), result.getRetCode());
        verify(fixture.payGatewayClient, never()).request(anyString(), anyMap());
    }

    @Test
    void gatewayBusinessFailureMapsToSystemErrorWithoutPersisting() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.paySignInfoMapper.selectBySeq(SEQ, null)).thenReturn(null);
        fixture.gatewayReturns(PaySignFacadeFixture.gatewayFailure(600, "操作失败"));

        RequestContractResultRespDTO result = fixture.service.requestContractResult(query(), "01");

        assertEquals(PaySignErrorCodeEnum.SYSTEM_ERROR.getCode(), result.getRetCode());
        verify(fixture.paySignInfoMapper, never()).insert(any());
        verify(fixture.paySignInfoMapper, never()).markSigned(anyString(), anyString(), anyString(), any());
    }

    @Test
    void existingRecordRefreshedToSignedGoesThroughMarkSigned() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        PaySignInfo local = signedLocalRecord();
        local.setContractStatus("NOT_SIGNED");
        local.setPayAccountId(null);
        local.setPayAgreementNo(null);
        when(fixture.paySignInfoMapper.selectBySeq(SEQ, null)).thenReturn(local);
        fixture.gatewayReturns(gatewaySignedData());
        when(fixture.paySignInfoMapper.markSigned(anyString(), anyString(), anyString(), any())).thenReturn(1);

        RequestContractResultRespDTO result = fixture.service.requestContractResult(query(), "01");

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        assertEquals("SIGNED", result.getStatus());
        verify(fixture.paySignInfoMapper).markSigned(anyString(), anyString(), anyString(), any());
    }

    @Test
    void orphanSignedContractIsReturnedButNeverPersisted() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.paySignInfoMapper.selectBySeq(SEQ, null)).thenReturn(null);
        fixture.gatewayReturns(gatewaySignedData());

        RequestContractResultRespDTO result = fixture.service.requestContractResult(query(), "01");

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        assertEquals("SIGNED", result.getStatus());
        assertEquals("PA-9", result.getPayAccountId());
        verify(fixture.paySignInfoMapper, never()).insert(any());
        verify(fixture.paySignInfoMapper, never()).markSigned(anyString(), anyString(), anyString(), any());
    }

    @Test
    void emptyGatewayDataWithoutLocalRecordFallsBackToNotSigned() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.paySignInfoMapper.selectBySeq(SEQ, null)).thenReturn(null);
        fixture.gatewayReturns(PaySignFacadeFixture.gatewaySuccess());

        RequestContractResultRespDTO result = fixture.service.requestContractResult(query(), "01");

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        assertEquals("NOT_SIGNED", result.getStatus());
        verify(fixture.paySignInfoMapper, never()).insert(any());
    }

    private RequestContractResultReqDTO query() {
        RequestContractResultReqDTO request = new RequestContractResultReqDTO();
        request.setThirdUserId(USER);
        request.setRequestSignSeq(SEQ);
        request.setPaymentVendor(ALIPAY_VENDOR);
        return request;
    }

    private PaySignInfo signedLocalRecord() {
        PaySignInfo signInfo = new PaySignInfo();
        signInfo.setRequestSignSeq(SEQ);
        signInfo.setThirdUserId(USER);
        signInfo.setPaymentVendor(ALIPAY_VENDOR);
        signInfo.setContractStatus("SIGNED");
        signInfo.setPayAccountId("PA-1");
        signInfo.setPayAgreementNo("AGR-1");
        return signInfo;
    }

    private PaySignGatewayResponse gatewaySignedData() {
        PaySignGatewayResponse response = PaySignFacadeFixture.gatewaySuccess();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "SIGNED");
        data.put("payUserId", "PA-9");
        data.put("payAgreementNo", "AGR-9");
        response.setData(data);
        return response;
    }
}
