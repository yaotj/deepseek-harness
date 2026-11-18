package com.chinasofti.huateng.paysign.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.chinasofti.huateng.model.app.PaySignCallbackResult;
import com.chinasofti.huateng.model.app.ReceiveSignResultReqDTO;
import com.chinasofti.huateng.paysign.constant.PaySignErrorCodeEnum;
import com.chinasofti.huateng.paysign.entity.AppTerminationRequest;
import com.chinasofti.huateng.paysign.entity.PaySignInfo;
import com.chinasofti.huateng.paysign.entity.PaySignRequest;
import com.chinasofti.huateng.paysign.event.SignResultCommittedEvent;
import com.chinasofti.huateng.model.app.ReceiveTerminationResultReqDTO;
import com.chinasofti.huateng.paysign.model.response.BaseRespDTO;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

class CallbackDomainCharacterizationTest {

    private static final String WALLET_VENDOR = "0B";
    private static final String ALIPAY_VENDOR = "03";
    private static final String SEQ = "0052290701523998";
    private static final String USER = "U-TEST-0002";

    @Test
    void signResultSuccessPublishesCommittedEventAndNeverNotifiesInsideTransaction() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();

        PaySignCallbackResult result = fixture.service.receiveSignResult(signResult("SUCCESS"), "01");

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        verify(fixture.eventPublisher).publishEvent(any(SignResultCommittedEvent.class));
        verifyNoInteractions(fixture.terminationNotifyService);
    }

    @Test
    void signResultSuccessInsertsSignedRecord() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();

        fixture.service.receiveSignResult(signResult("SUCCESS"), "01");

        ArgumentCaptor<PaySignInfo> captor = ArgumentCaptor.forClass(PaySignInfo.class);
        verify(fixture.paySignInfoMapper).insert(captor.capture());
        PaySignInfo inserted = captor.getValue();
        assertEquals(SEQ, inserted.getRequestSignSeq());
        assertEquals(USER, inserted.getThirdUserId());
        assertEquals("SIGNED", inserted.getContractStatus());
    }

    @Test
    void signResultFailureOnlyWritesFailedAuditLog() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();

        PaySignCallbackResult result = fixture.service.receiveSignResult(signResult("FAILED"), "01");

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        verify(fixture.paySignInfoMapper, never()).insert(any());
        verify(fixture.eventPublisher, never()).publishEvent(any(SignResultCommittedEvent.class));
        List<PaySignRequest> logs = fixture.auditLogs();
        assertEquals("FAILED", logs.get(logs.size() - 1).getSignStatus());
    }

    @Test
    void walletSignResultCallbackIsAcceptedAndPersisted() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        ReceiveSignResultReqDTO request = signResult("SUCCESS");
        request.setPaymentVendor(WALLET_VENDOR);

        PaySignCallbackResult result = fixture.service.receiveSignResult(request, "01");

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        ArgumentCaptor<PaySignInfo> captor = ArgumentCaptor.forClass(PaySignInfo.class);
        verify(fixture.paySignInfoMapper).insert(captor.capture());
        assertEquals(WALLET_VENDOR, captor.getValue().getPaymentVendor());
        assertEquals("SIGNED", captor.getValue().getContractStatus());
        verify(fixture.eventPublisher).publishEvent(any(SignResultCommittedEvent.class));
    }


    @Test
    void duplicateSignResultCallbackIsIdempotentAndNeverRenotifies() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        doThrow(new RuntimeException(new DuplicateKeyException("UK_APPSI_REQUEST_SIGN_SEQ")))
                .when(fixture.paySignInfoMapper).insert(any());
        when(fixture.paySignInfoMapper.selectSignStatusBySeq(SEQ)).thenReturn("SIGNED");

        PaySignCallbackResult result = fixture.service.receiveSignResult(signResult("SUCCESS"), "01");

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        verify(fixture.eventPublisher, never()).publishEvent(any(SignResultCommittedEvent.class));
        for (PaySignRequest logRecord : fixture.auditLogs()) {
            assertNull(logRecord.getNotifyStatus());
        }
    }

    @Test
    void nonConflictInsertFailureIsNotSwallowedAsReplay() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        doThrow(new IllegalStateException("ORA-00904")).when(fixture.paySignInfoMapper).insert(any());

        PaySignCallbackResult result = fixture.service.receiveSignResult(signResult("SUCCESS"), "01");

        assertEquals(PaySignErrorCodeEnum.SYSTEM_ERROR.getCode(), result.getRetCode());
        verify(fixture.eventPublisher, never()).publishEvent(any(SignResultCommittedEvent.class));
    }

    @Test
    void terminationCallbackAcceptsPendingRequestOnSuccess() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.terminationRequestMapper.selectByRequestSignSeq(SEQ)).thenReturn(termination("PENDING"));
        when(fixture.terminationRequestMapper.markPendingSuccess(eq(SEQ), any(LocalDateTime.class)))
                .thenReturn(1);

        BaseRespDTO result = fixture.service.receiveTerminationResult(terminationResult("SUCCESS"), "01");

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        verify(fixture.terminationRequestMapper).markPendingSuccess(eq(SEQ), any());
        verify(fixture.terminationRequestMapper).initChannelSyncPending(SEQ);
        verify(fixture.terminationNotifyService).asyncNotifyTerminationResult(any(), isNull(), any());
    }

    @Test
    void terminationCallbackIdempotentOnTerminalStatus() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.terminationRequestMapper.selectByRequestSignSeq(SEQ)).thenReturn(termination("SUCCESS"));

        BaseRespDTO result = fixture.service.receiveTerminationResult(terminationResult("SUCCESS"), "01");

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        verify(fixture.paySignInfoMapper, never()).deleteByUserAndVendor(anyString(), anyString());
    }

    @Test
    void terminationCallbackRejectsUnknownRequest() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.terminationRequestMapper.selectByRequestSignSeq(SEQ)).thenReturn(null);

        BaseRespDTO result = fixture.service.receiveTerminationResult(terminationResult("SUCCESS"), "01");

        assertEquals(PaySignErrorCodeEnum.TERMINATION_REQUEST_NOT_FOUND.getCode(), result.getRetCode());
        verify(fixture.paySignInfoMapper, never()).deleteByUserAndVendor(anyString(), anyString());
    }

    @Test
    void walletTerminationCallbackIsIgnoredButAnsweredSuccessfully() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        ReceiveTerminationResultReqDTO request = terminationResult("SUCCESS");
        request.setPaymentVendor(WALLET_VENDOR);

        BaseRespDTO result = fixture.service.receiveTerminationResult(request, "01");

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        verify(fixture.terminationRequestMapper, never()).selectByRequestSignSeq(anyString());
        verify(fixture.paySignInfoMapper, never()).deleteByUserAndVendor(anyString(), anyString());
    }

    private ReceiveSignResultReqDTO signResult(String status) {
        ReceiveSignResultReqDTO request = new ReceiveSignResultReqDTO();
        request.setRequestSignSeq(SEQ);
        request.setThirdUserId(USER);
        request.setPaymentVendor(ALIPAY_VENDOR);
        request.setDisplayAccount("138****8000");
        request.setStatus(status);
        return request;
    }

    private ReceiveTerminationResultReqDTO terminationResult(String status) {
        ReceiveTerminationResultReqDTO request = new ReceiveTerminationResultReqDTO();
        request.setRequestSignSeq(SEQ);
        request.setThirdUserId(USER);
        request.setPaymentVendor(ALIPAY_VENDOR);
        request.setStatus(status);
        return request;
    }

    private AppTerminationRequest termination(String terminationStatus) {
        AppTerminationRequest record = new AppTerminationRequest();
        record.setRequestSignSeq(SEQ);
        record.setThirdUserId(USER);
        record.setPaymentVendor(ALIPAY_VENDOR);
        record.setCardId("CARD-2");
        record.setCardType("0441");
        record.setTerminationStatus(terminationStatus);
        return record;
    }
}
