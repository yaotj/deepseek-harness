package com.chinasofti.huateng.paysign.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chinasofti.huateng.model.paysign.CompensateNotifyRespDTO;
import com.chinasofti.huateng.paysign.mapper.PayRefundDetailMapper;
import com.chinasofti.huateng.paysign.mapper.PayTxnDetailMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class PaymentRefundSummaryCompensationTest {

    private static final String DRIFTED_ORDER_NO = "GT20260915000000001";

    private static final String ORPHAN_ORDER_NO = "GT20260826192157073586419";

    private final PayRefundDetailMapper payRefundDetailMapper = mock(PayRefundDetailMapper.class);
    private final PayTxnDetailMapper payTxnDetailMapper = mock(PayTxnDetailMapper.class);

    private RefundDomainServiceImpl newService() {
        return new RefundDomainServiceImpl(
                payTxnDetailMapper,
                payRefundDetailMapper,
                null);
    }

    private void scanReturns(List<String> drifted, List<String> orphans) {
        when(payRefundDetailMapper.selectDriftedRefundSummary(anyString(), anyInt())).thenReturn(drifted);
        when(payRefundDetailMapper.selectOrphanRefundOrders(anyString(), anyInt())).thenReturn(orphans);
    }

    @Test
    void driftedOrderWithAffectedRowCountsAsSubmitted() {
        RefundDomainServiceImpl service = newService();
        scanReturns(List.of(DRIFTED_ORDER_NO), List.of());
        when(payTxnDetailMapper.updateRefundSummary(DRIFTED_ORDER_NO)).thenReturn(1);

        CompensateNotifyRespDTO response = service.compensateRefundSummary();

        assertEquals(1, response.getScanned(), "扫到 1 条 A 类");
        assertEquals(1, response.getSubmitted(), "重算影响 1 行 MUST 计入已修");
        assertEquals(0, response.getSkipped(), "没有未收口的行");
        verify(payTxnDetailMapper, times(1)).updateRefundSummary(DRIFTED_ORDER_NO);
    }

    @Test
    void driftedOrderWithZeroAffectedRowIsSkippedNotSubmitted() {
        RefundDomainServiceImpl service = newService();
        scanReturns(List.of(DRIFTED_ORDER_NO), List.of());
        when(payTxnDetailMapper.updateRefundSummary(DRIFTED_ORDER_NO)).thenReturn(0);

        CompensateNotifyRespDTO response = service.compensateRefundSummary();

        assertEquals(1, response.getScanned(), "扫到 1 条 A 类");
        assertEquals(0, response.getSubmitted(),
                "影响 0 行 MUST NOT 计入已修 —— 一行都没动，却对外宣称账已对齐是最坏的结果");
        assertEquals(1, response.getSkipped(), "MUST 计入本轮未收口，留给下一轮重扫");
    }

    @Test
    void orphanOrderIsNeverTouchedAndAlwaysSkipped() {
        RefundDomainServiceImpl service = newService();
        scanReturns(List.of(), List.of(ORPHAN_ORDER_NO));

        CompensateNotifyRespDTO response = service.compensateRefundSummary();

        assertEquals(1, response.getScanned(), "扫到 1 条 B 类");
        assertEquals(0, response.getSubmitted(), "B 类 NEVER 计入已修：本任务不会自愈它");
        assertEquals(1, response.getSkipped(), "B 类 MUST 全部计入 skipped 等人工");
        verify(payTxnDetailMapper, never()).updateRefundSummary(anyString());
    }

    @Test
    void bothScansEmptyReportsNothingScanned() {
        RefundDomainServiceImpl service = newService();
        scanReturns(List.of(), List.of());

        CompensateNotifyRespDTO response = service.compensateRefundSummary();

        assertEquals(0, response.getScanned(), "两类都空时 scanned MUST 为 0，否则调用方永远停不下来");
        assertEquals(0, response.getSubmitted(), "没有可修的行");
        assertEquals(0, response.getSkipped(), "没有跳过的行");
        verify(payTxnDetailMapper, never()).updateRefundSummary(anyString());
    }
}
