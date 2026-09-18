package com.chinasofti.huateng.alipay.paysign.service.impl;

import com.chinasofti.huateng.alipay.paysign.service.impl.notify.PaymentNotifyAdapter;
import com.chinasofti.huateng.alipay.paysign.service.impl.termination.AlipayTerminationInternalServiceImpl;
import com.chinasofti.huateng.alipay.paysign.service.impl.termination.TerminationNotifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chinasofti.huateng.alipay.paysign.mapper.AlipaySignInfoMapper;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipayTerminationRequestMapper;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import com.chinasofti.huateng.common.response.AlipayCommonResponse;
import com.chinasofti.huateng.model.alipaytrip.AlipayProcessTerminationReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayProcessTerminationRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfo;
import com.chinasofti.huateng.model.alipaytrip.AlipayTerminationRequest;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

/**
 * 特征断言：钉住销卡链路 —— {@code TerminationNotifier} 的三个 {@code Outcome} 与
 * {@code AlipayTerminationInternalServiceImpl} 的单批计数。
 *
 * <p>这条链路是**批次 1 的目标形态**：`AlipayContractServiceImpl.executeTermination` 已收口成
 * 委派本类，因为只有本类满足「先调远端、后改本地」。</p>
 *
 * <p>批次 2（ADR-D130）唯一的语义变化：签约行的状态写入由无条件覆盖的 {@code updateStatus}
 * 换成带前置状态断言的 CAS {@code markTerminated}（{@code SIGNED -> TERMINATED}）。
 * 因此本文件里凡是「远端成功」的用例都 MUST stub {@code markTerminated} 返 1，
 * <b>不 stub 就等于库里状态既不是 SIGNED 也不是 TERMINATED</b>，走的是 CONFLICT 分支。</p>
 */
class TerminationSweepCharacterizationTest {

    private static final String AGREEMENT_CODE = "AL20260917000000000000001";
    private static final String CHANNEL_AGREEMENT_CODE = "2088CHANNEL0001";
    private static final String TERMINATION_SEQ = "f0a1b2c3d4e5f60718293a4b5c6d7e8f";
    private static final String THIRD_USER_ID = "2088000000000001";

    private AlipaySignInfoMapper signInfoMapper;
    private AlipayTerminationRequestMapper terminationRequestMapper;
    private PaymentNotifyAdapter paymentNotifyAdapter;
    private TerminationNotifier notifier;

    @BeforeEach
    void setUp() throws ReflectiveOperationException {
        signInfoMapper = mock(AlipaySignInfoMapper.class);
        terminationRequestMapper = mock(AlipayTerminationRequestMapper.class);
        paymentNotifyAdapter = mock(PaymentNotifyAdapter.class);

        notifier = new TerminationNotifier();
        inject(notifier, "alipaySignInfoMapper", signInfoMapper);
        inject(notifier, "alipayTerminationRequestMapper", terminationRequestMapper);
        inject(notifier, "paymentNotifyAdapter", paymentNotifyAdapter);
    }

    // ---------- TerminationNotifier ----------

    @Test
    void remoteIsCalledBeforeLocalWriteAndCasClosesTheRegistration() {
        when(signInfoMapper.selectByAgreementCode(AGREEMENT_CODE)).thenReturn(signInfo());
        when(signInfoMapper.markTerminated(eq(AGREEMENT_CODE), any())).thenReturn(1);
        when(paymentNotifyAdapter.notifyCloseResult(anyString(), anyBoolean())).thenReturn(notifyAck("0000"));
        when(terminationRequestMapper.updateStatusCas(eq(TERMINATION_SEQ), eq("PENDING"), eq("COMPLETED"), any()))
                .thenReturn(1);

        TerminationNotifier.Outcome outcome = notifier.execute(terminationRequest());

        assertEquals(TerminationNotifier.Outcome.TERMINATED, outcome);
        InOrder order = inOrder(paymentNotifyAdapter, signInfoMapper, terminationRequestMapper);
        order.verify(paymentNotifyAdapter).notifyCloseResult(anyString(), anyBoolean());
        order.verify(signInfoMapper).markTerminated(eq(AGREEMENT_CODE), any());
        order.verify(terminationRequestMapper)
                .updateStatusCas(eq(TERMINATION_SEQ), eq("PENDING"), eq("COMPLETED"), any());
        verify(signInfoMapper, never()).selectSignStatusByAgreementCode(anyString());
    }

    /**
     * ADR-D130：CAS 命中就不回查，这是「枚举 + 白名单 + CAS」三件套规则的一部分。
     * 上一条用例末尾的 {@code never()} 已钉住，本条从反面钉住「返 0 才回查」。
     */
    @Test
    void signCasMissOnAlreadyTerminatedRowIsIdempotentAndStillClosesRegistration() {
        when(signInfoMapper.selectByAgreementCode(AGREEMENT_CODE)).thenReturn(signInfo());
        when(signInfoMapper.markTerminated(eq(AGREEMENT_CODE), any())).thenReturn(0);
        when(signInfoMapper.selectSignStatusByAgreementCode(AGREEMENT_CODE)).thenReturn("TERMINATED");
        when(paymentNotifyAdapter.notifyCloseResult(anyString(), anyBoolean())).thenReturn(notifyAck("0000"));
        when(terminationRequestMapper.updateStatusCas(eq(TERMINATION_SEQ), eq("PENDING"), eq("COMPLETED"), any()))
                .thenReturn(1);

        assertEquals(TerminationNotifier.Outcome.TERMINATED, notifier.execute(terminationRequest()),
                "签约行已是 TERMINATED 属重复执行，MUST 继续把登记收口成 COMPLETED，否则这条会永远重推");
    }

    /**
     * ADR-D130：CAS 返 0 且库里既不是 SIGNED 也不是 TERMINATED = CONFLICT。
     *
     * <p><b>NEVER 静默放行</b>：放行就会留下「签约状态不明、登记已 COMPLETED」这条谁也看不见的不一致。
     * 本轮返 {@code RETRY_LATER}、登记留在 PENDING，同时打 ERROR 交人工。</p>
     */
    @Test
    void signCasConflictKeepsRegistrationPendingForManualReview() {
        when(signInfoMapper.selectByAgreementCode(AGREEMENT_CODE)).thenReturn(signInfo());
        when(signInfoMapper.markTerminated(eq(AGREEMENT_CODE), any())).thenReturn(0);
        when(signInfoMapper.selectSignStatusByAgreementCode(AGREEMENT_CODE)).thenReturn(null);
        when(paymentNotifyAdapter.notifyCloseResult(anyString(), anyBoolean())).thenReturn(notifyAck("0000"));

        assertEquals(TerminationNotifier.Outcome.RETRY_LATER, notifier.execute(terminationRequest()));
        verify(terminationRequestMapper, never()).updateStatusCas(anyString(), anyString(), anyString(), any());
    }

    @Test
    void notifyAgreementNoPrefersChannelAgreementCode() {
        when(signInfoMapper.selectByAgreementCode(AGREEMENT_CODE)).thenReturn(signInfo());
        when(signInfoMapper.markTerminated(anyString(), any())).thenReturn(1);
        when(paymentNotifyAdapter.notifyCloseResult(anyString(), anyBoolean())).thenReturn(notifyAck("0000"));
        when(terminationRequestMapper.updateStatusCas(anyString(), anyString(), anyString(), any())).thenReturn(1);

        notifier.execute(terminationRequest());

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(paymentNotifyAdapter).notifyCloseResult(captor.capture(), eq(true));
        assertEquals(CHANNEL_AGREEMENT_CODE, captor.getValue(),
                "agreementNo MUST 送 CHANNEL_AGREEMENT_CODE，NEVER 送我方 agreementCode");
    }

    @Test
    void missingChannelAgreementCodeFallsBackToOurOwnCode() {
        AlipaySignInfo withoutChannelCode = signInfo();
        withoutChannelCode.setChannelAgreementCode("  ");
        when(signInfoMapper.selectByAgreementCode(AGREEMENT_CODE)).thenReturn(withoutChannelCode);
        when(signInfoMapper.markTerminated(anyString(), any())).thenReturn(1);
        when(paymentNotifyAdapter.notifyCloseResult(anyString(), anyBoolean())).thenReturn(notifyAck("0000"));
        when(terminationRequestMapper.updateStatusCas(anyString(), anyString(), anyString(), any())).thenReturn(1);

        notifier.execute(terminationRequest());

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(paymentNotifyAdapter).notifyCloseResult(captor.capture(), eq(true));
        assertEquals(AGREEMENT_CODE, captor.getValue(),
                "渠道号缺失属数据缺陷，退回我方号只为保留旧行为（支付中心大概率仍查不到）");
    }

    @Test
    void missingSignInfoIsTerminalFailure() {
        when(signInfoMapper.selectByAgreementCode(AGREEMENT_CODE)).thenReturn(null);

        TerminationNotifier.Outcome outcome = notifier.execute(terminationRequest());

        assertEquals(TerminationNotifier.Outcome.FAILED, outcome,
                "签约信息不存在是终态失败：重试多少次都变不出一条签约记录");
        verify(terminationRequestMapper).updateStatusCas(eq(TERMINATION_SEQ), eq("PENDING"), eq("FAIL"), any());
        verify(paymentNotifyAdapter, never()).notifyCloseResult(anyString(), anyBoolean());
    }

    @Test
    void nonSuccessPayCenterCodeKeepsRegistrationPending() {
        when(signInfoMapper.selectByAgreementCode(AGREEMENT_CODE)).thenReturn(signInfo());
        when(paymentNotifyAdapter.notifyCloseResult(anyString(), anyBoolean())).thenReturn(notifyAck("8999"));

        TerminationNotifier.Outcome outcome = notifier.execute(terminationRequest());

        assertEquals(TerminationNotifier.Outcome.RETRY_LATER, outcome);
        verify(signInfoMapper, never()).markTerminated(anyString(), any());
        verify(terminationRequestMapper, never()).updateStatusCas(anyString(), anyString(), anyString(), any());
    }

    @Test
    void nullPayCenterResponseKeepsRegistrationPending() {
        when(signInfoMapper.selectByAgreementCode(AGREEMENT_CODE)).thenReturn(signInfo());
        when(paymentNotifyAdapter.notifyCloseResult(anyString(), anyBoolean())).thenReturn(null);

        assertEquals(TerminationNotifier.Outcome.RETRY_LATER, notifier.execute(terminationRequest()));
        verify(signInfoMapper, never()).markTerminated(anyString(), any());
    }

    @Test
    void unknownExceptionNeverMarksFailBecauseFailIsTerminal() {
        when(signInfoMapper.selectByAgreementCode(AGREEMENT_CODE)).thenReturn(signInfo());
        when(paymentNotifyAdapter.notifyCloseResult(anyString(), anyBoolean())).thenReturn(notifyAck("0000"));
        doThrow(new IllegalStateException("模拟本地写库抖动"))
                .when(signInfoMapper).markTerminated(anyString(), any());

        TerminationNotifier.Outcome outcome = notifier.execute(terminationRequest());

        assertEquals(TerminationNotifier.Outcome.RETRY_LATER, outcome,
                "异常原因未知时 MUST 保持 PENDING 让下一轮自愈，NEVER 置 FAIL（FAIL 是永久卡死的终态）");
        verify(terminationRequestMapper, never()).updateStatusCas(anyString(), anyString(), eq("FAIL"), any());
    }

    @Test
    void casMissDoesNotCountAsTerminated() {
        when(signInfoMapper.selectByAgreementCode(AGREEMENT_CODE)).thenReturn(signInfo());
        when(signInfoMapper.markTerminated(anyString(), any())).thenReturn(1);
        when(paymentNotifyAdapter.notifyCloseResult(anyString(), anyBoolean())).thenReturn(notifyAck("0000"));
        when(terminationRequestMapper.updateStatusCas(anyString(), anyString(), anyString(), any())).thenReturn(0);

        assertEquals(TerminationNotifier.Outcome.RETRY_LATER, notifier.execute(terminationRequest()),
                "CAS 未命中说明已被别的执行流收口，本轮 MUST NOT 重复计数");
    }

    // ---------- 批处理 processTermination ----------

    @Test
    void batchCountsOutcomesAndSkipsRowsWithoutThirdUserId() throws ReflectiveOperationException {
        TerminationNotifier stubNotifier = mock(TerminationNotifier.class);
        AlipayTerminationRequest dirty = terminationRequest();
        dirty.setThirdUserId("   ");
        AlipayTerminationRequest ok = terminationRequest();
        when(terminationRequestMapper.selectByStatusLimit("PENDING", 200)).thenReturn(List.of(dirty, ok));
        when(stubNotifier.execute(ok)).thenReturn(TerminationNotifier.Outcome.TERMINATED);

        AlipayProcessTerminationRespDTO response = batchService(stubNotifier)
                .processTermination(new AlipayProcessTerminationReqDTO());

        assertEquals(FepAppErrorCodeEnum.SUCCESS.getCode(), response.getResultCode());
        assertEquals(2, response.getScanned());
        assertEquals(1, response.getTerminated());
        assertEquals(0, response.getFailed());
        assertEquals(1, response.getSkipped(), "缺 thirdUserId 的历史脏数据 MUST 计入 skipped 并留日志");
        verify(stubNotifier, never()).execute(dirty);
    }

    @Test
    void batchRejectsUnparsableReferenceTimeInsteadOfScanningEverything() throws ReflectiveOperationException {
        AlipayProcessTerminationReqDTO request = new AlipayProcessTerminationReqDTO();
        request.setReferenceTime("2026-09-17");

        AlipayProcessTerminationRespDTO response = batchService(mock(TerminationNotifier.class))
                .processTermination(request);

        assertEquals(FepAppErrorCodeEnum.INVALID_PARAM.getCode(), response.getResultCode(),
                "referenceTime 解析失败 MUST 返 8001，NEVER 静默退化成全表扫描");
        verify(terminationRequestMapper, never()).selectByStatusLimit(anyString(), anyInt());
        verify(terminationRequestMapper, never()).selectByStatusBefore(anyString(), any(), anyInt());
    }

    @Test
    void batchWithReferenceTimeNarrowsByCutoff() throws ReflectiveOperationException {
        AlipayProcessTerminationReqDTO request = new AlipayProcessTerminationReqDTO();
        request.setReferenceTime("20260916");
        when(terminationRequestMapper.selectByStatusBefore(eq("PENDING"), any(), eq(200))).thenReturn(List.of());

        AlipayProcessTerminationRespDTO response = batchService(mock(TerminationNotifier.class))
                .processTermination(request);

        assertEquals(FepAppErrorCodeEnum.SUCCESS.getCode(), response.getResultCode());
        assertEquals(0, response.getScanned());
        verify(terminationRequestMapper, never()).selectByStatusLimit(anyString(), anyInt());
    }

    @Test
    void batchQueryFailureIsReportedAsSystemError() throws ReflectiveOperationException {
        when(terminationRequestMapper.selectByStatusLimit("PENDING", 200))
                .thenThrow(new IllegalStateException("模拟查询失败"));

        AlipayProcessTerminationRespDTO response = batchService(mock(TerminationNotifier.class))
                .processTermination(new AlipayProcessTerminationReqDTO());

        assertEquals(FepAppErrorCodeEnum.SYSTEM_ERROR.getCode(), response.getResultCode());
    }

    // ---------- fixtures ----------

    private AlipayTerminationInternalServiceImpl batchService(TerminationNotifier stubNotifier)
            throws ReflectiveOperationException {
        AlipayTerminationInternalServiceImpl service = new AlipayTerminationInternalServiceImpl();
        inject(service, "alipayTerminationRequestMapper", terminationRequestMapper);
        inject(service, "terminationNotifier", stubNotifier);
        return service;
    }

    private AlipayTerminationRequest terminationRequest() {
        AlipayTerminationRequest request = new AlipayTerminationRequest();
        request.setTerminationSeq(TERMINATION_SEQ);
        request.setAgreementCode(AGREEMENT_CODE);
        request.setThirdUserId(THIRD_USER_ID);
        request.setStatus("PENDING");
        request.setCreateTime(LocalDateTime.now());
        return request;
    }

    private AlipaySignInfo signInfo() {
        AlipaySignInfo signInfo = new AlipaySignInfo();
        signInfo.setAgreementCode(AGREEMENT_CODE);
        signInfo.setChannelAgreementCode(CHANNEL_AGREEMENT_CODE);
        signInfo.setThirdUserId(THIRD_USER_ID);
        signInfo.setSignStatus("SIGNED");
        return signInfo;
    }

    private AlipayCommonResponse notifyAck(String retCode) {
        AlipayCommonResponse response = new AlipayCommonResponse();
        response.setRetCode(retCode);
        response.setRetMsg(FepAppErrorCodeEnum.SUCCESS.getCode().equals(retCode) ? "成功" : "未查询到协议信息");
        return response;
    }

    private static void inject(Object target, String fieldName, Object value) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
