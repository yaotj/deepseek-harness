package com.chinasofti.huateng.paysign.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

/** 护栏：签约成功回调只发事件不直接通知；解约回调只接受 SCANNING，终态幂等短路。 */
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

    /** 失败分支：不落签约主表、不发事件，流水的 SIGN_STATUS 落 FAILED。 */
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

    /** 钱包（{@code 0B}）签约成功回调必须被接受并落 {@code APP_PAY_SIGN_INFO}。 */
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

    /**
     * 渠道重推同一笔已成功的签约回调：撞唯一键即幂等返 {@code 0000}，
     * 且 <b>NEVER 再发事件、NEVER 再插 {@code NOTIFY_STATUS='PENDING'} 流水</b>（2026-09-17，ADR-D123）。
     *
     * <p>异常故意包一层 {@code RuntimeException} —— 本模块开了 tracing，观测切面会换类型（ADR-D53），
     * 裸 {@code catch (DuplicateKeyException)} 在线上根本进不去。
     */
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

    /** 非唯一键冲突的异常 MUST 继续按系统异常处置，NEVER 被幂等分支吞成成功。 */
    @Test
    void nonConflictInsertFailureIsNotSwallowedAsReplay() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        doThrow(new IllegalStateException("ORA-00904")).when(fixture.paySignInfoMapper).insert(any());

        PaySignCallbackResult result = fixture.service.receiveSignResult(signResult("SUCCESS"), "01");

        assertEquals(PaySignErrorCodeEnum.SYSTEM_ERROR.getCode(), result.getRetCode());
        verify(fixture.eventPublisher, never()).publishEvent(any(SignResultCommittedEvent.class));
    }

    @Test
    void terminationCallbackRejectsPendingRequest() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.terminationRequestMapper.selectByRequestSignSeq(SEQ)).thenReturn(termination("PENDING"));

        BaseRespDTO result = fixture.service.receiveTerminationResult(terminationResult("SUCCESS"), "01");

        assertEquals(PaySignErrorCodeEnum.TERMINATION_REQUEST_NOT_FOUND.getCode(), result.getRetCode());
        verify(fixture.paySignInfoMapper, never()).deleteByUserAndVendor(anyString(), anyString());
    }

    @Test
    void terminationCallbackIsIdempotentOnTerminalStatus() {
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

    /** 钱包解绑走 {@code requestAgreeRelease} 同步完成，解约回调只记审计并返幂等成功。 */
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
