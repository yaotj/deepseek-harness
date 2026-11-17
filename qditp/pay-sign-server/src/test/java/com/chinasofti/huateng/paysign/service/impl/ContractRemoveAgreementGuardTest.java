package com.chinasofti.huateng.paysign.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chinasofti.huateng.model.app.RequestAgreeReleaseReqDTO;
import com.chinasofti.huateng.model.app.RequestAgreeReleaseResult;
import com.chinasofti.huateng.paysign.constant.PaySignErrorCodeEnum;
import com.chinasofti.huateng.paysign.entity.PaySignInfo;
import org.junit.jupiter.api.Test;


class ContractRemoveAgreementGuardTest {

    private static final String SEQ = "0052290701523999";
    private static final String USER = "U-TEST-0001";
    private static final String ALIPAY_VENDOR = "03";
    private static final String METRO_APP = "01";

    @Test
    void blankAgreementCodeIsRejectedBeforeTouchingDatabase() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();

        RequestAgreeReleaseResult result = fixture.service.removeSignAgreement(request("  "), METRO_APP);

        assertEquals(PaySignErrorCodeEnum.INVALID_PARAM.getCode(), result.getRetCode());
        assertEquals(400, result.getCode());
        assertEquals(Boolean.FALSE, result.getSuccess());
        verify(fixture.paySignInfoMapper, never()).selectBySeq(anyString(), any());
        verify(fixture.paySignInfoMapper, never()).markUnsigned(anyString(), any());
    }

    @Test
    void missingSignRecordIsRejectedWithoutCas() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.paySignInfoMapper.selectBySeq(SEQ, null)).thenReturn(null);

        RequestAgreeReleaseResult result = fixture.service.removeSignAgreement(request(SEQ), METRO_APP);

        assertEquals(PaySignErrorCodeEnum.RECORD_NOT_EXIST.getCode(), result.getRetCode());
        assertEquals(404, result.getCode());
        assertEquals(Boolean.FALSE, result.getSuccess());
        verify(fixture.paySignInfoMapper, never()).markUnsigned(anyString(), any());
    }

    @Test
    void successfulCasAnswersSuccessAndPersistsUnsigned() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.paySignInfoMapper.selectBySeq(SEQ, null)).thenReturn(signedRecord());
        when(fixture.paySignInfoMapper.markUnsigned(anyString(), any())).thenReturn(1);

        RequestAgreeReleaseResult result = fixture.service.removeSignAgreement(request(SEQ), METRO_APP);

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        assertEquals(0, result.getCode());
        assertEquals(Boolean.TRUE, result.getSuccess());
        verify(fixture.paySignInfoMapper).markUnsigned(anyString(), any());
    }


    @Test
    void casMissOnAlreadyUnsignedIsIdempotentSuccess() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.paySignInfoMapper.selectBySeq(SEQ, null)).thenReturn(signedRecord());
        when(fixture.paySignInfoMapper.markUnsigned(anyString(), any())).thenReturn(0);
        when(fixture.paySignInfoMapper.selectSignStatusBySeq(SEQ)).thenReturn("UNSIGNED");

        RequestAgreeReleaseResult result = fixture.service.removeSignAgreement(request(SEQ), METRO_APP);

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        assertEquals(Boolean.TRUE, result.getSuccess());
    }


    @Test
    void casMissWithForeignStatusIsRejectedAsConflict() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.paySignInfoMapper.selectBySeq(SEQ, null)).thenReturn(signedRecord());
        when(fixture.paySignInfoMapper.markUnsigned(anyString(), any())).thenReturn(0);
        when(fixture.paySignInfoMapper.selectSignStatusBySeq(SEQ)).thenReturn("SIGN_FAILED");

        RequestAgreeReleaseResult result = fixture.service.removeSignAgreement(request(SEQ), METRO_APP);

        assertEquals(PaySignErrorCodeEnum.INVALID_PARAM.getCode(), result.getRetCode());
        assertEquals(409, result.getCode());
        assertEquals(Boolean.FALSE, result.getSuccess());
        assertTrue(result.getRetMsg().contains("SIGN_FAILED"),
                "拒绝文案 MUST 带上实测到的当前状态，否则运维只能猜是哪一态挡的");
    }

    private RequestAgreeReleaseReqDTO request(String agreementCode) {
        RequestAgreeReleaseReqDTO request = new RequestAgreeReleaseReqDTO();
        request.setAgreementCode(agreementCode);
        return request;
    }

    private PaySignInfo signedRecord() {
        PaySignInfo signInfo = new PaySignInfo();
        signInfo.setRequestSignSeq(SEQ);
        signInfo.setThirdUserId(USER);
        signInfo.setPaymentVendor(ALIPAY_VENDOR);
        signInfo.setContractStatus("SIGNED");
        return signInfo;
    }
}
