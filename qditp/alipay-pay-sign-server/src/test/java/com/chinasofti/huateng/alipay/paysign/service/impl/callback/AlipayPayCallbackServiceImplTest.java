package com.chinasofti.huateng.alipay.paysign.service.impl.callback;

import com.chinasofti.huateng.alipay.paysign.port.DebitSyncPort;
import com.chinasofti.huateng.alipay.paysign.service.impl.refund.RefundCallbackSettler;
import com.chinasofti.huateng.alipay.paysign.service.impl.refund.TxnRefundCallbackSettler;
import com.chinasofti.huateng.common.response.AlipayCommonResponse;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripPayNotifyReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRefundNotifyReqDTO;
import com.chinasofti.huateng.rpc.outcome.RpcOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 钉住 {@link AlipayPayCallbackServiceImpl} 的五条口径。
 *
 * <p>① {@code orderNo} 空时**连证据都不落**，{@code transStatus} 非法时**MUST 落一行 MANUAL 证据** ——
 * 两种非法形态处置不同，这正是 {@link PayNotifyCommand} 做成 sealed 三支的理由。
 *
 * <p>② {@code transStatus} 白名单只认 {@code 1}/{@code 2}，映射成 {@code SUCCESS}/{@code FAIL} 后才出网。
 *
 * <p>③ <b>先落证据、再计数</b>（{@code InOrder} 断言）。反序会让 COUNT 每次少一、限次永远差一轮才触发，
 * 而单次联调完全看不出来。
 *
 * <p>④ 同步失败时**未达上限返 9999 让对端重推、已达上限返 0000 让对端停推 + 标 MANUAL**。
 * 后者与「真成功」对外都是 {@code 0000}，只能靠 {@code HANDLE_STATUS} 区分。
 *
 * <p>⑤ 退款回调**恒返 0000**：先落 {@code PROCESSING} 证据、再按 {@code refundResult} 收口退款明细，
 * 最后按收口归宿回写处置状态；{@code REFUND_ORDER_NO} 取 {@code outRefundNo}。
 */
class AlipayPayCallbackServiceImplTest {

    private static final String ORDER_NO = "GT20260918021300000000001";
    private static final String CALLBACK_SEQ = "9f1c2d3e4a5b6c7d8e9f0a1b2c3d4e5f";

    private CallbackLogRepository callbackLogRepository;
    private PayTxnCallbackWriter payTxnCallbackWriter;
    private DebitSyncPort debitSyncPort;
    private RefundCallbackSettler refundCallbackSettler;
    private TxnRefundCallbackSettler txnRefundCallbackSettler;
    private AlipayPayCallbackServiceImpl service;

    @BeforeEach
    void setUp() {
        callbackLogRepository = mock(CallbackLogRepository.class);
        payTxnCallbackWriter = mock(PayTxnCallbackWriter.class);
        debitSyncPort = mock(DebitSyncPort.class);
        refundCallbackSettler = mock(RefundCallbackSettler.class);
        txnRefundCallbackSettler = mock(TxnRefundCallbackSettler.class);
        service = new AlipayPayCallbackServiceImpl(callbackLogRepository, payTxnCallbackWriter, debitSyncPort,
                refundCallbackSettler, txnRefundCallbackSettler);
        when(callbackLogRepository.recordPayCallback(any(), anyString(), any())).thenReturn(CALLBACK_SEQ);
        when(callbackLogRepository.recordRefundCallback(any(), any(), anyString(), any())).thenReturn(CALLBACK_SEQ);
        when(callbackLogRepository.countPayPush(ORDER_NO)).thenReturn(1);
        when(debitSyncPort.syncDebitStatus(anyString(), anyString(), anyString())).thenReturn(new RpcOutcome.Ok());
        when(refundCallbackSettler.settle(anyString(), anyString(), anyString(), any()))
                .thenReturn(RefundCallbackSettler.Outcome.SETTLED_SUCCESS);
    }

    @Test
    void missingOrderNoIsRejectedWithoutRecordingEvidence() {
        AlipayCommonResponse response = service.handlePayNotify(payNotify(null, "1"));

        assertEquals("8001", response.getRetCode());
        assertEquals("参数异常：orderNo不能为空", response.getRetMsg());
        verify(callbackLogRepository, never()).recordPayCallback(any(), anyString(), any());
        verify(debitSyncPort, never()).syncDebitStatus(anyString(), anyString(), anyString());
    }

    @Test
    void illegalTransStatusRecordsManualEvidenceAndNeverSyncs() {
        AlipayCommonResponse response = service.handlePayNotify(payNotify(ORDER_NO, "9"));

        assertEquals("8001", response.getRetCode());
        assertEquals("参数异常：transStatus 取值非法", response.getRetMsg());
        verify(callbackLogRepository).recordPayCallback(any(), eq("MANUAL"), eq("transStatus 非法: 9"));
        verify(debitSyncPort, never()).syncDebitStatus(anyString(), anyString(), anyString());
    }

    @Test
    void transStatusOneIsSyncedAsSuccess() {
        AlipayCommonResponse response = service.handlePayNotify(payNotify(ORDER_NO, "1"));

        verify(debitSyncPort).syncDebitStatus(ORDER_NO, "SUCCESS", "支付宝出行扣费结果回调");
        verify(callbackLogRepository).updateHandleResult(CALLBACK_SEQ, "SUCCESS", null);
        assertEquals("0000", response.getRetCode());
        assertEquals("成功", response.getRetMsg());
    }

    @Test
    void transStatusTwoIsSyncedAsFail() {
        service.handlePayNotify(payNotify(ORDER_NO, "2"));

        verify(debitSyncPort).syncDebitStatus(ORDER_NO, "FAIL", "支付宝出行扣费结果回调");
    }

    @Test
    void evidenceIsInsertedBeforeCounting() {
        service.handlePayNotify(payNotify(ORDER_NO, "1"));

        InOrder ordered = inOrder(callbackLogRepository);
        ordered.verify(callbackLogRepository).recordPayCallback(any(), eq("PROCESSING"), isNull());
        ordered.verify(callbackLogRepository).countPayPush(ORDER_NO);
    }

    /**
     * 回写支付明细 MUST 在同步扣费订单**之前**，且 MUST 把报文的 {@code transTime} 一起带下去 ——
     * 支付宝出行记录应答的 {@code payOrderNoDate} 取的就是它落进 {@code ALIPAY_PAY_TXN_DETAIL.TRANS_TIME}
     * 的值；这里断言的 {@code 20260918021500} 是报文原文，**NEVER 改成格式化后的样子**（列是 VARCHAR2、
     * 全链路不解析）。
     */
    @Test
    void payStatusIsWrittenToTxnDetailBeforeSyncingGateTxnPay() {
        service.handlePayNotify(payNotify(ORDER_NO, "1"));

        InOrder ordered = inOrder(payTxnCallbackWriter, debitSyncPort);
        ordered.verify(payTxnCallbackWriter).applyCallback(ORDER_NO, "SUCCESS", "2026091822001", "20260918021500");
        ordered.verify(debitSyncPort).syncDebitStatus(ORDER_NO, "SUCCESS", "支付宝出行扣费结果回调");
    }

    @Test
    void illegalTransStatusNeverWritesBackToTxnDetail() {
        service.handlePayNotify(payNotify(ORDER_NO, "9"));

        verify(payTxnCallbackWriter, never()).applyCallback(anyString(), anyString(), any(), any());
    }

    @Test
    void syncFailureBelowLimitAsksPayCenterToRetry() {
        when(debitSyncPort.syncDebitStatus(anyString(), anyString(), anyString()))
                .thenReturn(new RpcOutcome.BizRejected("9999", "扣费订单不存在"));

        AlipayCommonResponse response = service.handlePayNotify(payNotify(ORDER_NO, "1"));

        verify(callbackLogRepository).updateHandleResult(CALLBACK_SEQ, "FAIL", "扣费订单状态同步失败，待支付中心重推");
        verify(callbackLogRepository, never()).markPayManual(anyString(), anyString());
        assertEquals("9999", response.getRetCode());
        assertEquals("扣费订单状态同步失败", response.getRetMsg());
    }

    @Test
    void syncFailureAtLimitStopsRepushAndMarksManual() {
        when(callbackLogRepository.countPayPush(ORDER_NO)).thenReturn(2);
        when(debitSyncPort.syncDebitStatus(anyString(), anyString(), anyString()))
                .thenReturn(new RpcOutcome.Unreachable(new IllegalStateException("连接超时")));

        AlipayCommonResponse response = service.handlePayNotify(payNotify(ORDER_NO, "1"));

        verify(callbackLogRepository).markPayManual(ORDER_NO, "扣费订单状态同步失败");
        verify(callbackLogRepository, never()).updateHandleResult(anyString(), anyString(), any());
        assertEquals("0000", response.getRetCode(), "达到上限 MUST 返 0000 让支付中心停推");
    }

    @Test
    void refundMissingOrderNoIsRejectedWithoutRecordingEvidence() {
        AlipayCommonResponse response = service.handleRefundNotify(refundNotify(null, "400"));

        assertEquals("8001", response.getRetCode());
        assertEquals("参数异常：orderNo不能为空", response.getRetMsg());
        verify(callbackLogRepository, never()).recordRefundCallback(any(), any(), anyString(), any());
        verify(refundCallbackSettler, never()).settle(anyString(), anyString(), anyString(), any());
    }

    @Test
    void refundEvidenceTakesOutRefundNoAndAlwaysReturnsSuccess() {
        AlipayCommonResponse response = service.handleRefundNotify(refundNotify(ORDER_NO, "400"));

        RefundNotifyCommand.Accepted captured = capturedRefundCommand();
        assertEquals("R20260918000001", captured.refundOrderNo(), "REFUND_ORDER_NO MUST 取 outRefundNo、NEVER 取 refundNo");
        assertEquals(400, captured.refundAmount());
        assertEquals("SUCCESS", captured.refundResult(), "refundResult 落原值、不做映射");
        assertEquals("0000", response.getRetCode());
        assertEquals("成功", response.getRetMsg());
    }

    /**
     * 留证据 MUST 在收口明细**之前** —— 收口过程中抛异常那一轮，若反序写就什么痕迹都不留。
     * 同时钉住收口的入参：{@code refundOrderNo} 取 {@code outRefundNo}、描述取 {@code refundResultDesc}。
     */
    @Test
    void refundEvidenceIsRecordedBeforeSettlingDetail() {
        service.handleRefundNotify(refundNotify(ORDER_NO, "400"));

        InOrder ordered = inOrder(callbackLogRepository, refundCallbackSettler);
        ordered.verify(callbackLogRepository).recordRefundCallback(any(), any(), eq("PROCESSING"), isNull());
        ordered.verify(refundCallbackSettler).settle(ORDER_NO, "R20260918000001", "SUCCESS", "退款成功");
        verify(callbackLogRepository).updateHandleResult(CALLBACK_SEQ, "SUCCESS", "退款明细已收口为 SUCCESS，汇总已重算");
    }

    /** {@code PROCESSING} 不是失败：处置状态留在 {@code PROCESSING} 等终态回调，仍返 {@code 0000}。 */
    @Test
    void refundStillProcessingKeepsHandleStatusProcessing() {
        when(refundCallbackSettler.settle(anyString(), anyString(), anyString(), any()))
                .thenReturn(RefundCallbackSettler.Outcome.STILL_PROCESSING);

        AlipayCommonResponse response = service.handleRefundNotify(refundNotify(ORDER_NO, "400"));

        verify(callbackLogRepository).updateHandleResult(CALLBACK_SEQ, "PROCESSING", "退款仍处理中，等待终态回调");
        assertEquals("0000", response.getRetCode());
    }

    /** {@code refundResult} 取值不在契约内时转人工，但**仍返 0000** —— 让对端重推不会带来新信息。 */
    @Test
    void refundUnknownResultIsMarkedManualButStillStopsRepush() {
        when(refundCallbackSettler.settle(anyString(), anyString(), anyString(), any()))
                .thenReturn(RefundCallbackSettler.Outcome.UNKNOWN_RESULT);

        AlipayCommonResponse response = service.handleRefundNotify(refundNotify(ORDER_NO, "400"));

        verify(callbackLogRepository).updateHandleResult(CALLBACK_SEQ, "MANUAL", "refundResult 取值不在契约内: SUCCESS");
        assertEquals("0000", response.getRetCode(), "退款回调 MUST 恒返 0000、NEVER 靠 9999 引对端重推");
    }

    @Test
    void unparsableRefundAmountLeavesColumnEmptyInsteadOfFailing() {
        AlipayCommonResponse response = service.handleRefundNotify(refundNotify(ORDER_NO, "4.00元"));

        assertNull(capturedRefundCommand().refundAmount(), "解析不出 MUST 留空、NEVER 抛异常丢掉整行证据");
        assertEquals("0000", response.getRetCode());
    }

    private RefundNotifyCommand.Accepted capturedRefundCommand() {
        ArgumentCaptor<RefundNotifyCommand.Accepted> captor = ArgumentCaptor.forClass(RefundNotifyCommand.Accepted.class);
        verify(callbackLogRepository).recordRefundCallback(any(), captor.capture(), anyString(), any());
        return captor.getValue();
    }

    private AlipayTripPayNotifyReqDTO payNotify(String orderNo, String transStatus) {
        AlipayTripPayNotifyReqDTO request = new AlipayTripPayNotifyReqDTO();
        request.setOrderNo(orderNo);
        request.setTransStatus(transStatus);
        request.setChannelVoucherId("2026091822001");
        request.setTransAmount("400");
        request.setTransTime("20260918021500");
        return request;
    }

    private AlipayTripRefundNotifyReqDTO refundNotify(String orderNo, String refundAmount) {
        AlipayTripRefundNotifyReqDTO request = new AlipayTripRefundNotifyReqDTO();
        request.setOrderNo(orderNo);
        request.setOutRefundNo("R20260918000001");
        request.setRefundNo("PC20260918000001");
        request.setRefundAmount(refundAmount);
        request.setRefundResult("SUCCESS");
        request.setRefundResultDesc("退款成功");
        return request;
    }
}
