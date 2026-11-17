package com.chinasofti.huateng.paysign.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chinasofti.huateng.model.alipaytrip.AlipayTripAddContractReqDTO;
import com.chinasofti.huateng.model.app.RequestSignInfoResult;
import com.chinasofti.huateng.paysign.constant.PaySignErrorCodeEnum;
import com.chinasofti.huateng.paysign.entity.PaySignInfo;
import com.chinasofti.huateng.paysign.entity.PaySignRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AlipayTripSignCharacterizationTest {

    private static final String USER = "U-TEST-0007";
    private static final String AGREEMENT_CODE = "2088ALIPAYTRIP0001";
    private static final String ALIPAY_TRIP_VENDOR = "05";

    @Test
    void writesSignedRecordLocallyWithoutCallingPayCenter() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.paySignInfoMapper.selectByUserAndVendor(USER, ALIPAY_TRIP_VENDOR)).thenReturn(null);

        RequestSignInfoResult result = fixture.service.alipayTripRequestSignInfo(request());

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        ArgumentCaptor<PaySignInfo> captor = ArgumentCaptor.forClass(PaySignInfo.class);
        verify(fixture.paySignInfoMapper).insert(captor.capture());
        PaySignInfo inserted = captor.getValue();
        assertEquals(AGREEMENT_CODE, inserted.getRequestSignSeq());
        assertEquals(USER, inserted.getThirdUserId());
        assertEquals(ALIPAY_TRIP_VENDOR, inserted.getPaymentVendor());
        assertEquals("ALIPAY", inserted.getSignChannel());
        assertEquals("SIGNED", inserted.getContractStatus());
        verify(fixture.payGatewayClient, never()).request(anyString(), anyMap());
    }

    @Test
    void neverReturnsSdkInfo() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.paySignInfoMapper.selectByUserAndVendor(USER, ALIPAY_TRIP_VENDOR)).thenReturn(null);

        RequestSignInfoResult result = fixture.service.alipayTripRequestSignInfo(request());

        assertNull(result.getRequestStartSdkInfo());
    }

    @Test
    void alreadySignedIsRejectedWithoutWriting() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        PaySignInfo existing = new PaySignInfo();
        existing.setRequestSignSeq(AGREEMENT_CODE);
        when(fixture.paySignInfoMapper.selectByUserAndVendor(USER, ALIPAY_TRIP_VENDOR)).thenReturn(existing);

        RequestSignInfoResult result = fixture.service.alipayTripRequestSignInfo(request());

        assertEquals(PaySignErrorCodeEnum.ALREADY_SIGNED.getCode(), result.getRetCode());
        verify(fixture.paySignInfoMapper, never()).insert(any());
    }

    @Test
    void successWritesOneMergedAuditRow() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.paySignInfoMapper.selectByUserAndVendor(USER, ALIPAY_TRIP_VENDOR)).thenReturn(null);

        fixture.service.alipayTripRequestSignInfo(request());

        List<PaySignRequest> logs = fixture.auditLogs();
        assertEquals(1, logs.size(), "合并后 MUST 只有一行；两行是分叉前的旧现状");

        PaySignRequest audited = logs.get(0);
        assertEquals("SIGN", audited.getOperationType(), "OPERATION_TYPE 落库只有 SIGN / UNSIGN 两个值");
        assertEquals("SIGNED", audited.getSignStatus(), "合并行 MUST 保留原手写行的 SIGN_STATUS");
        assertNotNull(audited.getRequestBody(), "合并行 MUST 带报文");
        assertEquals("0000", audited.getResultCode(), "成功分支的审计行 MUST 带 retCode");
    }

    @Test
    void rejectedRequestStillLeavesAuditRow() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        PaySignInfo existing = new PaySignInfo();
        when(fixture.paySignInfoMapper.selectByUserAndVendor(USER, ALIPAY_TRIP_VENDOR)).thenReturn(existing);

        fixture.service.alipayTripRequestSignInfo(request());

        List<PaySignRequest> logs = fixture.auditLogs();
        assertEquals(1, logs.size());
        assertEquals("SIGN", logs.get(0).getOperationType());
        assertEquals(PaySignErrorCodeEnum.ALREADY_SIGNED.getCode(), logs.get(0).getResultCode());
    }

    private AlipayTripAddContractReqDTO request() {
        AlipayTripAddContractReqDTO request = new AlipayTripAddContractReqDTO();
        request.setThirdUserId(USER);
        request.setAgreementCode(AGREEMENT_CODE);
        request.setChannel(ALIPAY_TRIP_VENDOR);
        request.setChannelUserAccount("138****8000");
        return request;
    }
}
