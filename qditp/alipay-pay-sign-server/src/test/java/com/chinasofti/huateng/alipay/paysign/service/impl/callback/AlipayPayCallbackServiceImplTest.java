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


    @Test
    void refundEvidenceIsRecordedBeforeSettlingDetail() {
        service.handleRefundNotify(refundNotify(ORDER_NO, "400"));

        InOrder ordered = inOrder(callbackLogRepository, refundCallbackSettler);
        ordered.verify(callbackLogRepository).recordRefundCallback(any(), any(), eq("PROCESSING"), isNull());
        ordered.verify(refundCallbackSettler).settle(ORDER_NO, "R20260918000001", "SUCCESS", "退款成功");
        verify(callbackLogRepository).updateHandleResult(CALLBACK_SEQ, "SUCCESS", "退款明细已收口为 SUCCESS，汇总已重算");
    }

    @Test
    void refundStillProcessingKeepsHandleStatusProcessing() {
        when(refundCallbackSettler.settle(anyString(), anyString(), anyString(), any()))
                .thenReturn(RefundCallbackSettler.Outcome.STILL_PROCESSING);

        AlipayCommonResponse response = service.handleRefundNotify(refundNotify(ORDER_NO, "400"));

        verify(callbackLogRepository).updateHandleResult(CALLBACK_SEQ, "PROCESSING", "退款仍处理中，等待终态回调");
        assertEquals("0000", response.getRetCode());
    }

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
