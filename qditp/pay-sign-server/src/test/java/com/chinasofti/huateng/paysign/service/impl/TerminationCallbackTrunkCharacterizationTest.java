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

/** 护栏：解约回调主干的顺序（APP 通知先于通道清理）与 CAS 三分支的通知、留痕差异。 */
class TerminationCallbackTrunkCharacterizationTest {

    private static final String ALIPAY_VENDOR = "03";
    private static final String SEQ = "0052290701523997";
    private static final String USER = "U-TEST-0003";
    private static final String CARD_ID = "CARD-3";
    private static final String CARD_TYPE = "0441";

    /** 成功主干：删签约 → 落 UNSIGNED 流水 → CAS 收口 → 置通道待投递 → 通知 APP → 投递通道清理。 */
    @Test
    void successTrunkClosesLocallyThenNotifiesBeforeDeliveringChannelSync() {
        PaySignFacadeFixture fixture = scanningFixture();
        when(fixture.terminationRequestMapper.markSuccess(anyString(), any(LocalDateTime.class))).thenReturn(1);

        BaseRespDTO result = fixture.service.receiveTerminationResult(terminationResult("SUCCESS"), "01");

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        verify(fixture.paySignInfoMapper).deleteByUserAndVendor(USER, ALIPAY_VENDOR);
        verify(fixture.terminationRequestMapper).initChannelSyncPending(SEQ);

        InOrder order = inOrder(fixture.appNotifyService, fixture.channelSyncDeliverer);
        order.verify(fixture.appNotifyService).asyncNotifyTerminationResult(any(), any(), any());
        order.verify(fixture.channelSyncDeliverer)
                .deliver(SEQ, USER, ALIPAY_VENDOR, CARD_ID, CARD_TYPE);
    }

    /** 主干写的签约流水：SIGN_STATUS 是解约口径的 UNSIGNED，不是 SignStatus.FAILED 那个同名字面量。 */
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

    /** CAS 未命中且回查是 FAILED（expireScanning 抢先）：NEVER 补发成功通知、NEVER 置通道待投递。 */
    @Test
    void successConflictOnFailedSkipsNotifyAndMarksManualReview() {
        PaySignFacadeFixture fixture = scanningFixture();
        when(fixture.terminationRequestMapper.markSuccess(anyString(), any(LocalDateTime.class))).thenReturn(0);
        when(fixture.terminationRequestMapper.selectTerminationStatusBySeq(SEQ)).thenReturn("FAILED");

        BaseRespDTO result = fixture.service.receiveTerminationResult(terminationResult("SUCCESS"), "01");

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        verify(fixture.appNotifyService, never()).asyncNotifyTerminationResult(any(), any(), any());
        verify(fixture.channelSyncDeliverer, never())
                .deliver(anyString(), anyString(), anyString(), anyString(), anyString());
        verify(fixture.terminationRequestMapper, never()).initChannelSyncPending(anyString());
        verify(fixture.terminationRequestMapper)
                .markConflictForManualReview(anyString(), anyString(), anyString());
    }

    /** CAS 未命中但回查已是 SUCCESS（另一路先收口）：同样不通知、不投递。 */
    @Test
    void successIdempotentSkipsNotifyWithoutManualReview() {
        PaySignFacadeFixture fixture = scanningFixture();
        when(fixture.terminationRequestMapper.markSuccess(anyString(), any(LocalDateTime.class))).thenReturn(0);
        when(fixture.terminationRequestMapper.selectTerminationStatusBySeq(SEQ)).thenReturn("SUCCESS");

        BaseRespDTO result = fixture.service.receiveTerminationResult(terminationResult("SUCCESS"), "01");

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        verify(fixture.appNotifyService, never()).asyncNotifyTerminationResult(any(), any(), any());
        verify(fixture.channelSyncDeliverer, never())
                .deliver(anyString(), anyString(), anyString(), anyString(), anyString());
        verify(fixture.terminationRequestMapper, never()).initChannelSyncPending(anyString());
        verify(fixture.terminationRequestMapper, never())
                .markConflictForManualReview(anyString(), anyString(), anyString());
    }

    /** 失败主干：通知 APP 解约失败、NEVER 删签约记录、流水落 FAILED。 */
    @Test
    void failureTrunkNotifiesFailedAndKeepsSignInfo() {
        PaySignFacadeFixture fixture = scanningFixture();
        when(fixture.terminationRequestMapper.rejectScanning(anyString(), anyString(), any(LocalDateTime.class)))
                .thenReturn(1);

        BaseRespDTO result = fixture.service.receiveTerminationResult(terminationResult("FAILED"), "01");

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        ArgumentCaptor<NotifyTerminationFailedReqDTO> captor =
                ArgumentCaptor.forClass(NotifyTerminationFailedReqDTO.class);
        verify(fixture.appNotifyService).asyncNotifyTerminationFailed(any(), captor.capture());
        assertEquals(SEQ, captor.getValue().getRequestSignSeq());
        assertEquals("FAILED", captor.getValue().getFailReason());
        verify(fixture.paySignInfoMapper, never()).deleteByUserAndVendor(anyString(), anyString());

        List<PaySignRequest> logs = fixture.auditLogs();
        assertEquals("FAILED", logs.get(logs.size() - 1).getSignStatus());
    }

    /** 本次答复失败、库内已 SUCCESS：NEVER 发失败通知（会把「已解约」说成「解约失败」），MUST 留痕。 */
    @Test
    void failureConflictOnSuccessSkipsNotifyAndMarksManualReview() {
        PaySignFacadeFixture fixture = scanningFixture();
        when(fixture.terminationRequestMapper.rejectScanning(anyString(), anyString(), any(LocalDateTime.class)))
                .thenReturn(0);
        when(fixture.terminationRequestMapper.selectTerminationStatusBySeq(SEQ)).thenReturn("SUCCESS");

        fixture.service.receiveTerminationResult(terminationResult("FAILED"), "01");

        verify(fixture.appNotifyService, never()).asyncNotifyTerminationFailed(any(), any());
        verify(fixture.terminationRequestMapper)
                .markFailureConflictForManualReview(anyString(), anyString(), anyString());
    }

    /** 申请处于 SCANNING —— 这是进主干的唯一前置状态（白名单，不是「非终态即可」）。 */
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

    /** cardId / cardType 刻意不塞进回调报文：主干 MUST 从解约申请表补齐（2026-09-09 事故）。 */
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
