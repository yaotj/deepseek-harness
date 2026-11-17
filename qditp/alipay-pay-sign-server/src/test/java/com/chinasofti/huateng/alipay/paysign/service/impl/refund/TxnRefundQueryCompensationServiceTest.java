package com.chinasofti.huateng.alipay.paysign.service.impl.refund;

import com.chinasofti.huateng.alipay.paysign.entity.AlipayRefundTxnDetail;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipayRefundTxnDetailMapper;
import com.chinasofti.huateng.alipay.paysign.port.PayCenterPort;
import com.chinasofti.huateng.alipay.paysign.port.PayCenterReply;
import com.chinasofti.huateng.model.domain.OutboxScan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


class TxnRefundQueryCompensationServiceTest {

    private static final String ORDER_NO = "GT20260921021300000000001";
    private static final String REFUND_ORDER_NO = "R1789899554336b230c487";

    private AlipayRefundTxnDetailMapper alipayRefundTxnDetailMapper;
    private TxnRefundCallbackSettler txnRefundCallbackSettler;
    private PayCenterPort payCenterPort;
    private TxnRefundQueryCompensationService service;

    @BeforeEach
    void setUp() {
        alipayRefundTxnDetailMapper = mock(AlipayRefundTxnDetailMapper.class);
        txnRefundCallbackSettler = mock(TxnRefundCallbackSettler.class);
        payCenterPort = mock(PayCenterPort.class);
        service = new TxnRefundQueryCompensationService(alipayRefundTxnDetailMapper, txnRefundCallbackSettler,
                payCenterPort);

        when(alipayRefundTxnDetailMapper.selectCompensableRefundQuery(anyInt(), anyInt(), anyInt()))
                .thenReturn(List.of(processingRow()));
        when(alipayRefundTxnDetailMapper.delayNextRefundQuery(anyString(), anyInt())).thenReturn(1);
    }

    @Test
    void emptyScanNeverCallsPayCenter() {
        when(alipayRefundTxnDetailMapper.selectCompensableRefundQuery(anyInt(), anyInt(), anyInt()))
                .thenReturn(List.of());

        OutboxScan.Result scan = service.compensate();

        assertEquals(new OutboxScan.Result(0, 0, 0), scan);
        verify(payCenterPort, never()).refundQuery(any());
    }

    @Test
    void outboundBizDataCarriesBothRefundKeys() {
        when(payCenterPort.refundQuery(any())).thenReturn(accepted("SUCCESS", "SUCCESS"));
        when(txnRefundCallbackSettler.settle(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(RefundCallbackSettler.Outcome.SETTLED_SUCCESS);

        service.compensate();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(payCenterPort).refundQuery(captor.capture());
        assertEquals(REFUND_ORDER_NO, captor.getValue().get("refundOrderNo"));
        assertEquals(REFUND_ORDER_NO, captor.getValue().get("merchantRefundNo"));
    }

    @Test
    void successGoesThroughTheSharedSettlerAndNeverDelays() {
        when(payCenterPort.refundQuery(any())).thenReturn(accepted("SUCCESS", "SUCCESS"));
        when(txnRefundCallbackSettler.settle(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(RefundCallbackSettler.Outcome.SETTLED_SUCCESS);

        OutboxScan.Result scan = service.compensate();

        verify(txnRefundCallbackSettler).settle(eq(ORDER_NO), eq(REFUND_ORDER_NO), eq("SUCCESS"), anyString());
        verify(alipayRefundTxnDetailMapper, never()).delayNextRefundQuery(anyString(), anyInt());
        assertEquals(new OutboxScan.Result(1, 1, 0), scan);
    }

    @Test
    void failGoesThroughTheSharedSettler() {
        when(payCenterPort.refundQuery(any())).thenReturn(accepted("SUCCESS", "FAIL"));
        when(txnRefundCallbackSettler.settle(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(RefundCallbackSettler.Outcome.SETTLED_FAIL);

        OutboxScan.Result scan = service.compensate();

        verify(txnRefundCallbackSettler).settle(eq(ORDER_NO), eq(REFUND_ORDER_NO), eq("FAIL"), anyString());
        assertEquals(new OutboxScan.Result(1, 1, 0), scan);
    }

    @Test
    void unknownStatusKeepsProcessingAndDelaysNextQuery() {
        when(payCenterPort.refundQuery(any())).thenReturn(accepted("SUCCESS", "REFUNDING"));

        OutboxScan.Result scan = service.compensate();

        verify(txnRefundCallbackSettler, never()).settle(anyString(), anyString(), anyString(), anyString());
        verify(alipayRefundTxnDetailMapper).delayNextRefundQuery(eq(REFUND_ORDER_NO), eq(300));
        assertEquals(new OutboxScan.Result(1, 0, 1), scan);
    }

    @Test
    void bizRejectedReplyKeepsProcessingAndDelaysNextQuery() {
        when(payCenterPort.refundQuery(any())).thenReturn(accepted("9999", null));

        OutboxScan.Result scan = service.compensate();

        verify(txnRefundCallbackSettler, never()).settle(anyString(), anyString(), anyString(), anyString());
        verify(alipayRefundTxnDetailMapper).delayNextRefundQuery(eq(REFUND_ORDER_NO), eq(300));
        assertEquals(new OutboxScan.Result(1, 0, 1), scan);
    }

    @Test
    void gatewayRejectedKeepsProcessingAndDelaysNextQuery() {
        when(payCenterPort.refundQuery(any()))
                .thenReturn(new PayCenterReply.Rejected(600, null, "操作失败", "{\"code\":600}"));

        OutboxScan.Result scan = service.compensate();

        verify(txnRefundCallbackSettler, never()).settle(anyString(), anyString(), anyString(), anyString());
        verify(alipayRefundTxnDetailMapper).delayNextRefundQuery(eq(REFUND_ORDER_NO), eq(300));
        assertEquals(new OutboxScan.Result(1, 0, 1), scan);
    }

    @Test
    void noAnswerReplyKeepsProcessingAndDelaysNextQuery() {
        when(payCenterPort.refundQuery(any())).thenReturn(new PayCenterReply.NoAnswer());

        OutboxScan.Result scan = service.compensate();

        verify(txnRefundCallbackSettler, never()).settle(anyString(), anyString(), anyString(), anyString());
        verify(alipayRefundTxnDetailMapper).delayNextRefundQuery(eq(REFUND_ORDER_NO), eq(300));
        assertEquals(new OutboxScan.Result(1, 0, 1), scan);
    }

    @Test
    void casMissIsNotCountedAsSettled() {
        when(payCenterPort.refundQuery(any())).thenReturn(accepted("SUCCESS", "SUCCESS"));
        when(txnRefundCallbackSettler.settle(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(RefundCallbackSettler.Outcome.NOT_MATCHED);

        OutboxScan.Result scan = service.compensate();

        assertEquals(new OutboxScan.Result(1, 0, 1), scan);
    }

    @Test
    void singleRowFailureDoesNotBreakTheBatch() {
        when(payCenterPort.refundQuery(any())).thenThrow(new IllegalStateException("网关连接被重置"));

        OutboxScan.Result scan = service.compensate();

        assertEquals(new OutboxScan.Result(1, 0, 1), scan);
    }

    private PayCenterReply accepted(String retCode, String status) {
        Map<String, Object> data = new HashMap<>();
        if (status != null) {
            data.put("status", status);
        }
        return new PayCenterReply.Accepted(200, Boolean.TRUE, "ok", "{}", retCode, "ok", data);
    }

    private AlipayRefundTxnDetail processingRow() {
        AlipayRefundTxnDetail row = new AlipayRefundTxnDetail();
        row.setOrderNo(ORDER_NO);
        row.setRefundOrderNo(REFUND_ORDER_NO);
        row.setRefundStatus("PROCESSING");
        row.setRefundAmount(300);
        row.setTxnDate("20260921");
        return row;
    }
}
