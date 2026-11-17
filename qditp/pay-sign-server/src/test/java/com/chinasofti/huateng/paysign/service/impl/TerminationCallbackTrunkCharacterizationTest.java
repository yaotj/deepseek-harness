package com.chinasofti.huateng.paysign.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chinasofti.huateng.model.app.ReceiveTerminationResultReqDTO;
import com.chinasofti.huateng.paysign.constant.PaySignErrorCodeEnum;
import com.chinasofti.huateng.paysign.entity.AppTerminationRequest;
import com.chinasofti.huateng.paysign.entity.PaySignRequest;
import com.chinasofti.huateng.paysign.model.request.NotifyTerminationFailedReqDTO;
import com.chinasofti.huateng.paysign.model.response.BaseRespDTO;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class TerminationCallbackTrunkCharacterizationTest {

    private static final String ALIPAY_VENDOR = "03";
    private static final String SEQ = "0052290701523997";
    private static final String USER = "U-TEST-0003";
    private static final String CARD_ID = "CARD-3";
    private static final String CARD_TYPE = "0441";

    @Test
    void successTrunkClosesLocallyThenNotifiesBeforeDeliveringChannelSync() {
        PaySignFacadeFixture fixture = scanningFixture();
        when(fixture.terminationRequestMapper.markSuccess(anyString(), any(LocalDateTime.class))).thenReturn(1);

        BaseRespDTO result = fixture.service.receiveTerminationResult(terminationResult("SUCCESS"), "01");

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        verify(fixture.paySignInfoMapper).deleteByUserAndVendor(USER, ALIPAY_VENDOR);
        verify(fixture.terminationRequestMapper).initChannelSyncPending(SEQ);

        InOrder order = inOrder(fixture.terminationNotifyService, fixture.channelSyncDeliverer);
        order.verify(fixture.terminationNotifyService).asyncNotifyTerminationResult(any(), any(), any());
        order.verify(fixture.channelSyncDeliverer)
                .deliver(SEQ, USER, ALIPAY_VENDOR, CARD_ID, CARD_TYPE);
    }

    @Test
    void successTrunkWritesUnsignedSignLog() {
        PaySignFacadeFixture fixture = scanningFixture();
        when(fixture.terminationRequestMapper.markSuccess(anyString(), any(LocalDateTime.class))).thenReturn(1);

        fixture.service.receiveTerminationResult(terminationResult("SUCCESS"), "01");

        ArgumentCaptor<PaySignRequest> captor = ArgumentCaptor.forClass(PaySignRequest.class);
        verify(fixture.paySignRequestMapper, org.mockito.Mockito.atLeastOnce()).insert(captor.capture());
        PaySignRequest trunkLog = captor.getAllValues().stream()
                .filter(log -> "RECEIVE_TERMINATION_RESULT".equals(log.getOperationType()))
                .filter(log -> "UNSIGNED".equals(log.getSignStatus()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("主干未写 UNSIGNED 流水"));
        assertEquals(SEQ, trunkLog.getRequestSignSeq());
        assertEquals(CARD_ID, trunkLog.getCardId());
        assertEquals(CARD_TYPE, trunkLog.getCardType());
        assertEquals("PENDING", trunkLog.getNotifyStatus());
    }

    @Test
    void successConflictOnFailedSkipsNotifyAndMarksManualReview() {
        PaySignFacadeFixture fixture = scanningFixture();
        when(fixture.terminationRequestMapper.markSuccess(anyString(), any(LocalDateTime.class))).thenReturn(0);
        when(fixture.terminationRequestMapper.selectTerminationStatusBySeq(SEQ)).thenReturn("FAILED");

        BaseRespDTO result = fixture.service.receiveTerminationResult(terminationResult("SUCCESS"), "01");

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        verify(fixture.terminationNotifyService, never()).asyncNotifyTerminationResult(any(), any(), any());
        verify(fixture.channelSyncDeliverer, never())
                .deliver(anyString(), anyString(), anyString(), anyString(), anyString());
        verify(fixture.terminationRequestMapper, never()).initChannelSyncPending(anyString());
        verify(fixture.terminationRequestMapper)
                .markConflictForManualReview(anyString(), anyString(), anyString());
    }

    @Test
    void successIdempotentSkipsNotifyWithoutManualReview() {
        PaySignFacadeFixture fixture = scanningFixture();
        when(fixture.terminationRequestMapper.markSuccess(anyString(), any(LocalDateTime.class))).thenReturn(0);
        when(fixture.terminationRequestMapper.selectTerminationStatusBySeq(SEQ)).thenReturn("SUCCESS");

        BaseRespDTO result = fixture.service.receiveTerminationResult(terminationResult("SUCCESS"), "01");

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        verify(fixture.terminationNotifyService, never()).asyncNotifyTerminationResult(any(), any(), any());
        verify(fixture.channelSyncDeliverer, never())
                .deliver(anyString(), anyString(), anyString(), anyString(), anyString());
        verify(fixture.terminationRequestMapper, never()).initChannelSyncPending(anyString());
        verify(fixture.terminationRequestMapper, never())
                .markConflictForManualReview(anyString(), anyString(), anyString());
    }

    @Test
    void failureTrunkNotifiesFailedAndKeepsSignInfo() {
        PaySignFacadeFixture fixture = scanningFixture();
        when(fixture.terminationRequestMapper.rejectScanning(anyString(), anyString(), any(LocalDateTime.class)))
                .thenReturn(1);

        BaseRespDTO result = fixture.service.receiveTerminationResult(terminationResult("FAILED"), "01");

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        ArgumentCaptor<NotifyTerminationFailedReqDTO> captor =
                ArgumentCaptor.forClass(NotifyTerminationFailedReqDTO.class);
        verify(fixture.terminationNotifyService).asyncNotifyTerminationFailed(any(), captor.capture());
        assertEquals(SEQ, captor.getValue().getRequestSignSeq());
        assertEquals("FAILED", captor.getValue().getFailReason());
        verify(fixture.paySignInfoMapper, never()).deleteByUserAndVendor(anyString(), anyString());

        List<PaySignRequest> logs = fixture.auditLogs();
        assertEquals("FAILED", logs.get(logs.size() - 1).getSignStatus());
    }

    @Test
    void failureConflictOnSuccessSkipsNotifyAndMarksManualReview() {
        PaySignFacadeFixture fixture = scanningFixture();
        when(fixture.terminationRequestMapper.rejectScanning(anyString(), anyString(), any(LocalDateTime.class)))
                .thenReturn(0);
        when(fixture.terminationRequestMapper.selectTerminationStatusBySeq(SEQ)).thenReturn("SUCCESS");

        fixture.service.receiveTerminationResult(terminationResult("FAILED"), "01");

        verify(fixture.terminationNotifyService, never()).asyncNotifyTerminationFailed(any(), any());
        verify(fixture.terminationRequestMapper)
                .markFailureConflictForManualReview(anyString(), anyString(), anyString());
    }

    private PaySignFacadeFixture scanningFixture() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.terminationRequestMapper.selectByRequestSignSeq(SEQ)).thenReturn(termination());
        return fixture;
    }

    private ReceiveTerminationResultReqDTO terminationResult(String status) {
        ReceiveTerminationResultReqDTO request = new ReceiveTerminationResultReqDTO();
        request.setRequestSignSeq(SEQ);
        request.setThirdUserId(USER);
        request.setPaymentVendor(ALIPAY_VENDOR);
        request.setStatus(status);
        return request;
    }

    private AppTerminationRequest termination() {
        AppTerminationRequest record = new AppTerminationRequest();
        record.setRequestSignSeq(SEQ);
        record.setThirdUserId(USER);
        record.setPaymentVendor(ALIPAY_VENDOR);
        record.setCardId(CARD_ID);
        record.setCardType(CARD_TYPE);
        record.setTerminationStatus("SCANNING");
        return record;
    }
}
