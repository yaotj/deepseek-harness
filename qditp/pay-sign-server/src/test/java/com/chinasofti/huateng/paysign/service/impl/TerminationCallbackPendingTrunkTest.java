package com.chinasofti.huateng.paysign.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chinasofti.huateng.model.app.ReceiveTerminationResultReqDTO;
import com.chinasofti.huateng.paysign.constant.PaySignErrorCodeEnum;
import com.chinasofti.huateng.paysign.entity.AppTerminationRequest;
import com.chinasofti.huateng.paysign.model.request.NotifyTerminationFailedReqDTO;
import com.chinasofti.huateng.paysign.model.response.BaseRespDTO;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/**
 * PENDING 状态下外部解约结果回调的兜底处理：
 * 当 Quartz 未能将 PENDING 转为 SCANNING（未结清校验超时/拒答），但支付平台已先行完成解约时，
 * 本类覆盖直接收口的四条路径。
 */
class TerminationCallbackPendingTrunkTest {

    private static final String ALIPAY_VENDOR = "03";
    private static final String SEQ = "0052301801524728";
    private static final String USER = "00523018";
    private static final String CARD_ID = "0426090949000508";
    private static final String CARD_TYPE = "02";

    @Test
    @DisplayName("PENDING + SUCCESS 回调：直接收口并通知 APP、触发渠道同步")
    void pendingSuccessClosesDirectlyAndNotifies() {
        PaySignFacadeFixture fixture = pendingFixture();
        when(fixture.terminationRequestMapper.markPendingSuccess(eq(SEQ), any(LocalDateTime.class)))
                .thenReturn(1);

        BaseRespDTO result = fixture.service.receiveTerminationResult(terminationResult("SUCCESS"), "01");

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        verify(fixture.terminationRequestMapper).markPendingSuccess(eq(SEQ), any(LocalDateTime.class));
        verify(fixture.terminationRequestMapper).initChannelSyncPending(SEQ);
        InOrder order = inOrder(fixture.terminationNotifyService, fixture.channelSyncDeliverer);
        order.verify(fixture.terminationNotifyService).asyncNotifyTerminationResult(any(), any(), any());
        order.verify(fixture.channelSyncDeliverer).deliver(SEQ, USER, ALIPAY_VENDOR, CARD_ID, CARD_TYPE);
    }

    @Test
    @DisplayName("PENDING + FAILED 回调：转 FAILED 并通知 APP 解约失败")
    void pendingFailureTransitsToFailedAndNotifies() {
        PaySignFacadeFixture fixture = pendingFixture();
        when(fixture.terminationRequestMapper.markPendingFailed(eq(SEQ), any(String.class), any(LocalDateTime.class)))
                .thenReturn(1);

        BaseRespDTO result = fixture.service.receiveTerminationResult(terminationResult("FAILED"), "01");

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        verify(fixture.terminationRequestMapper).markPendingFailed(eq(SEQ), any(String.class), any(LocalDateTime.class));
        verify(fixture.terminationNotifyService).asyncNotifyTerminationFailed(any(), any(NotifyTerminationFailedReqDTO.class));
        verify(fixture.terminationRequestMapper, never()).initChannelSyncPending(any());
    }

    @Test
    @DisplayName("PENDING + SUCCESS 回调：CAS 落空且库内已是 SUCCESS 时按幂等跳过，不重复通知")
    void pendingSuccessIdempotentWhenAlreadySuccess() {
        PaySignFacadeFixture fixture = pendingFixture();
        when(fixture.terminationRequestMapper.markPendingSuccess(eq(SEQ), any(LocalDateTime.class)))
                .thenReturn(0);
        when(fixture.terminationRequestMapper.selectTerminationStatusBySeq(SEQ)).thenReturn("SUCCESS");

        BaseRespDTO result = fixture.service.receiveTerminationResult(terminationResult("SUCCESS"), "01");

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        verify(fixture.terminationNotifyService, never()).asyncNotifyTerminationResult(any(), any(), any());
        verify(fixture.channelSyncDeliverer, never()).deliver(any(), any(), any(), any(), any());
        verify(fixture.terminationRequestMapper, never()).markConflictForManualReview(any(), any(), any());
    }

    @Test
    @DisplayName("PENDING + SUCCESS 回调：CAS 落空且库内已 FAILED 时留痕人工核对")
    void pendingSuccessConflictOnFailedMarksManualReview() {
        PaySignFacadeFixture fixture = pendingFixture();
        when(fixture.terminationRequestMapper.markPendingSuccess(eq(SEQ), any(LocalDateTime.class)))
                .thenReturn(0);
        when(fixture.terminationRequestMapper.selectTerminationStatusBySeq(SEQ)).thenReturn("FAILED");

        BaseRespDTO result = fixture.service.receiveTerminationResult(terminationResult("SUCCESS"), "01");

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        verify(fixture.terminationRequestMapper).markConflictForManualReview(eq(SEQ), any(), any());
    }

    @Test
    @DisplayName("PENDING + FAILED 回调：CAS 落空且库内已 SUCCESS 时留痕人工核对")
    void pendingFailureConflictOnSuccessMarksManualReview() {
        PaySignFacadeFixture fixture = pendingFixture();
        when(fixture.terminationRequestMapper.markPendingFailed(eq(SEQ), any(String.class), any(LocalDateTime.class)))
                .thenReturn(0);
        when(fixture.terminationRequestMapper.selectTerminationStatusBySeq(SEQ)).thenReturn("SUCCESS");

        fixture.service.receiveTerminationResult(terminationResult("FAILED"), "01");

        verify(fixture.terminationRequestMapper).markFailureConflictForManualReview(eq(SEQ), any(), any());
        verify(fixture.terminationNotifyService, never()).asyncNotifyTerminationFailed(any(), any());
    }

    private PaySignFacadeFixture pendingFixture() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.terminationRequestMapper.selectByRequestSignSeq(SEQ)).thenReturn(pendingRecord());
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

    private AppTerminationRequest pendingRecord() {
        AppTerminationRequest record = new AppTerminationRequest();
        record.setRequestSignSeq(SEQ);
        record.setThirdUserId(USER);
        record.setPaymentVendor(ALIPAY_VENDOR);
        record.setCardId(CARD_ID);
        record.setCardType(CARD_TYPE);
        record.setTerminationStatus("PENDING");
        return record;
    }
}
