package com.chinasofti.huateng.alipay.paysign.service.impl.refund;

import com.chinasofti.huateng.alipay.paysign.entity.AlipayRefundLog;
import com.chinasofti.huateng.alipay.paysign.port.PayCenterPort;
import com.chinasofti.huateng.alipay.paysign.port.PayCenterReply;
import com.chinasofti.huateng.model.domain.OutboxScan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 钉住 {@link RefundQueryCompensationService} 的五条口径。
 *
 * <p>①<b>出网 bizData MUST 同时带 {@code refundOrderNo} 与 {@code merchantRefundNo}</b>（ADR-D92 实测：
 * 只送前者时网关返 9999，表现为退款单永久空转而端点每轮返 0000）。
 *
 * <p>②<b>回查到 SUCCESS：先 CAS 收口明细、再刷汇总</b>，顺序反了汇总算的是旧值。
 *
 * <p>③<b>回查到 FAIL：收口明细但 NEVER 刷汇总</b>（钱没退出去）。
 *
 * <p>④<b>业务 {@code retCode} 非成功 / {@code Rejected} / {@code NoAnswer} / {@code status} 判不出终态：
 * 一律不回写、保持 PROCESSING</b> —— 钱可能已经退了，落 FAIL 会让这笔被当成没退成、随后被人再退一次。
 *
 * <p>⑤<b>CAS 命中 0 行不算收口</b>（已被回调抢先收口），且那一轮 NEVER 刷汇总。
 */
class RefundQueryCompensationServiceTest {

    private static final String ORDER_NO = "GT20260920021300000000001";
    private static final String REFUND_ORDER_NO = "R1758300000000abcd1234";

    private RefundLogRepository refundLogRepository;
    private PayCenterPort payCenterPort;
    private RefundQueryCompensationService service;

    @BeforeEach
    void setUp() {
        refundLogRepository = mock(RefundLogRepository.class);
        payCenterPort = mock(PayCenterPort.class);
        service = new RefundQueryCompensationService(refundLogRepository, payCenterPort);

        when(refundLogRepository.scanCompensable(anyInt(), anyInt(), anyInt()))
                .thenReturn(List.of(processingRow()));
        when(refundLogRepository.settleFromCallback(anyString(), anyString(), anyString())).thenReturn(1);
    }

    @Test
    void emptyScanNeverCallsPayCenter() {
        when(refundLogRepository.scanCompensable(anyInt(), anyInt(), anyInt())).thenReturn(List.of());

        OutboxScan.Result scan = service.compensate();

        assertEquals(new OutboxScan.Result(0, 0, 0), scan);
        verify(payCenterPort, never()).refundQuery(any());
    }

    @Test
    void outboundBizDataCarriesBothRefundKeys() {
        when(payCenterPort.refundQuery(any())).thenReturn(accepted("SUCCESS", "SUCCESS"));

        service.compensate();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(payCenterPort).refundQuery(captor.capture());
        assertEquals(REFUND_ORDER_NO, captor.getValue().get("refundOrderNo"));
        assertEquals(REFUND_ORDER_NO, captor.getValue().get("merchantRefundNo"));
    }

    @Test
    void successSettlesDetailBeforeRefreshingSummary() {
        when(payCenterPort.refundQuery(any())).thenReturn(accepted("SUCCESS", "SUCCESS"));

        OutboxScan.Result scan = service.compensate();

        InOrder order = inOrder(refundLogRepository);
        order.verify(refundLogRepository).settleFromCallback(eq(REFUND_ORDER_NO), eq("SUCCESS"), anyString());
        order.verify(refundLogRepository).refreshSummary(ORDER_NO, REFUND_ORDER_NO);
        assertEquals(new OutboxScan.Result(1, 1, 0), scan);
    }

    @Test
    void failSettlesDetailAndNeverRefreshesSummary() {
        when(payCenterPort.refundQuery(any())).thenReturn(accepted("SUCCESS", "FAIL"));

        OutboxScan.Result scan = service.compensate();

        verify(refundLogRepository).settleFromCallback(eq(REFUND_ORDER_NO), eq("FAIL"), anyString());
        verify(refundLogRepository, never()).refreshSummary(anyString(), anyString());
        assertEquals(new OutboxScan.Result(1, 1, 0), scan);
    }

    @Test
    void bizRejectedReplyNeverTouchesDetail() {
        when(payCenterPort.refundQuery(any())).thenReturn(accepted("9999", null));

        OutboxScan.Result scan = service.compensate();

        verify(refundLogRepository, never()).settleFromCallback(anyString(), anyString(), anyString());
        verify(refundLogRepository, never()).refreshSummary(anyString(), anyString());
        assertEquals(new OutboxScan.Result(1, 0, 1), scan);
    }

    @Test
    void unknownStatusKeepsProcessing() {
        when(payCenterPort.refundQuery(any())).thenReturn(accepted("SUCCESS", "REFUNDING"));

        OutboxScan.Result scan = service.compensate();

        verify(refundLogRepository, never()).settleFromCallback(anyString(), anyString(), anyString());
        assertEquals(new OutboxScan.Result(1, 0, 1), scan);
    }

    @Test
    void rejectedReplyKeepsProcessing() {
        when(payCenterPort.refundQuery(any()))
                .thenReturn(new PayCenterReply.Rejected(600, null, "操作失败", "{\"code\":600}"));

        OutboxScan.Result scan = service.compensate();

        verify(refundLogRepository, never()).settleFromCallback(anyString(), anyString(), anyString());
        assertEquals(new OutboxScan.Result(1, 0, 1), scan);
    }

    @Test
    void noAnswerReplyKeepsProcessing() {
        when(payCenterPort.refundQuery(any())).thenReturn(new PayCenterReply.NoAnswer());

        OutboxScan.Result scan = service.compensate();

        verify(refundLogRepository, never()).settleFromCallback(anyString(), anyString(), anyString());
        assertEquals(new OutboxScan.Result(1, 0, 1), scan);
    }

    @Test
    void casMissNeverRefreshesSummaryAndIsNotCountedAsSettled() {
        when(payCenterPort.refundQuery(any())).thenReturn(accepted("SUCCESS", "SUCCESS"));
        when(refundLogRepository.settleFromCallback(anyString(), anyString(), anyString())).thenReturn(0);

        OutboxScan.Result scan = service.compensate();

        verify(refundLogRepository, never()).refreshSummary(anyString(), anyString());
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

    private AlipayRefundLog processingRow() {
        AlipayRefundLog row = new AlipayRefundLog();
        row.setRefundSeq("2c9f0a1b3d4e4f5a8b6c7d8e9f012345");
        row.setOrderNo(ORDER_NO);
        row.setRefundOrderNo(REFUND_ORDER_NO);
        row.setRefundStatus("PROCESSING");
        row.setRefundAmount("300");
        return row;
    }
}
