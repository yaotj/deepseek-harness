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

/**
 * {@code receiveTerminationResult} <b>主干</b>的特征测试（护栏，2026-09-16）。
 *
 * <p><b>为什么单独建一个类</b>：{@link CallbackDomainCharacterizationTest} 里那四条解约用例
 * 全部在**入口段就返回**了（钱包分流 / 申请不存在 / 终态幂等 / 非 SCANNING 拒绝），
 * 四条断言清一色是 {@code verify(deleteByUserAndVendor, never())} —— 也就是说
 * <b>「进了主干之后会发生什么」一行都没有被执行过</b>。逐方法量覆盖时确认：
 * {@code :339~489} 那 148 行（本地事务收口 + CAS 三分支 + 通知 + 通道清理投递）零覆盖。
 * <b>「grep 到方法名」NEVER 等于「被覆盖」</b>，这个类就是那次量化的产物。
 *
 * <p>钉住的都是方法体内写着 NEVER / MUST 的决定，而它们此前只靠注释维持：
 * <ul>
 *   <li><b>APP 通知 MUST 在通道清理投递之前</b>：account-server 慢或不可达时不该把 APP 通知拖住，
 *       而通道清理已落 {@code PENDING}、补偿一定会重推。顺序写反编译照样通过。</li>
 *   <li><b>{@code markSuccess} 与 {@code initChannelSyncPending} 只在本次调用真收口时成对发生</b>：
 *       IDEMPOTENT 也去置 PENDING 会把已 SUCCESS 的通道同步打回待投递、通道被重复删一次。</li>
 *   <li><b>CONFLICT / IDEMPOTENT 两条都 NEVER 补发成功通知</b>，但只有 CONFLICT 落人工核对留痕
 *       —— 两者合并就会把「双路撞同一个 CAS」这种设计内常态报成需人工核对（2026-09-14 实测）。</li>
 *   <li><b>失败分支 NEVER 删签约记录</b>，且流水的 {@code SIGN_STATUS} 落 {@code FAILED}。</li>
 * </ul>
 *
 * <p>断言一律经 {@code fixture.service} 下钻，理由见 {@link PaySignFacadeFixture} 的门面注释。
 */
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

        // 顺序本身就是不变量：通知 MUST 先于出网删通道。
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

    /**
     * CAS 未命中且回查是 FAILED（expireScanning 抢先）：NEVER 补发成功通知、NEVER 置通道待投递，
     * MUST 落人工核对留痕。
     */
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

    /**
     * CAS 未命中但回查已是 SUCCESS（另一路先收口）：同样不通知、不投递，
     * 但 <b>NEVER 落人工核对</b> —— 双路驱动撞同一个 CAS 是设计内常态。
     */
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
