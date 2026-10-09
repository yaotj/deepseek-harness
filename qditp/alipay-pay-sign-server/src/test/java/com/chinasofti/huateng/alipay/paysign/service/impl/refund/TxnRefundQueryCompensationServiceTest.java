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

/**
 * 钉住 {@link TxnRefundQueryCompensationService}（**新表**退款回查补偿）的六条口径。
 *
 * <p>①<b>出网 bizData MUST 同时带 {@code refundOrderNo} 与 {@code merchantRefundNo}</b>（ADR-D92 实测：
 * 只送前者时网关返 9999，表现为退款单永久空转而端点每轮返 0000）。
 *
 * <p>②<b>拿到终态就交给 {@link TxnRefundCallbackSettler} 收口</b> —— 明细 CAS + 仅成功时重算汇总
 * 全项目只有那一份实现，<b>NEVER 在本服务里抄第二份</b>。
 *
 * <p>③<b>未得终态（业务码非成功 / Rejected / NoAnswer / status 判不出）一律不回写、保持 PROCESSING</b>，
 * 钱可能已经退了，落 FAIL 会让这笔被当成没退成、随后被人再退一次。
 *
 * <p>④<b>未得终态 MUST 推退避时间</b>（本表有 {@code NEXT_REQUEST_TIME}，与旧表不同），
 * 否则一条永远拿不到终态的单子每轮都打一次支付中心。
 *
 * <p>⑤<b>拿到终态那一支 NEVER 推退避</b>：已经是终态了，再推时间只会留下误导性的字段值。
 *
 * <p>⑥<b>CAS 未命中（{@code NOT_MATCHED}）不算收口</b>（被回调抢先收口了）。
 */
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

    /** 单条回查抛异常时整批不中断，该行计入 failed。 */
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
