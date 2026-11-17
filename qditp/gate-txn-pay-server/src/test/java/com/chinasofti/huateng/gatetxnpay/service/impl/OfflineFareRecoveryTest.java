package com.chinasofti.huateng.gatetxnpay.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.chinasofti.huateng.gatetxnpay.entity.GateTxnPay;
import com.chinasofti.huateng.gatetxnpay.fare.FareCalculator;
import com.chinasofti.huateng.gatetxnpay.fare.FareDataGateway;
import com.chinasofti.huateng.gatetxnpay.mapper.GateTxnPayMapper;
import com.chinasofti.huateng.gatetxnpay.paysign.AlipayTripPayRequestFactory;
import com.chinasofti.huateng.gatetxnpay.paysign.GatePayRequestFactory;
import com.chinasofti.huateng.gatetxnpay.paysign.PayInitiationRpcAdapter;
import com.chinasofti.huateng.gatetxnpay.paysign.PaySignInitiator;
import com.chinasofti.huateng.gatetxnpay.station.StationNameBackfiller;
import com.chinasofti.huateng.gatetxnpay.writer.GateTxnPayWriter;
import com.chinasofti.huateng.rpc.alipay.paysign.AlipayPaySignClient;
import com.chinasofti.huateng.rpc.paySign.PaySignClient;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OfflineFareRecoveryTest {

    private final GateTxnPayMapper mapper = mock(GateTxnPayMapper.class);
    private final GateTxnPayWriter writer = mock(GateTxnPayWriter.class);
    private final FareCalculator fareCalculator = mock(FareCalculator.class);
    private final PaySignClient paySignClient = mock(PaySignClient.class);
    private final AlipayPaySignClient alipayPaySignClient = mock(AlipayPaySignClient.class);

    @Test
    void emptyBatchWritesNothing() {
        when(mapper.selectOfflineFarePending(anyString(), anyString(), anyInt())).thenReturn(List.of());

        assertEquals(0, service().recoverOfflineFarePendingOrders(50, 7));
        verifyNoInteractions(writer);
        verifyNoInteractions(fareCalculator);
        verifyNoInteractions(paySignClient);
    }

    @Test
    void batchSizeAndLookbackAreClamped() {
        assertEquals(50, capturedBatchSize(0, 7));
        assertEquals(50, capturedBatchSize(-1, 7));
        assertEquals(200, capturedBatchSize(9999, 7));
        assertEquals(30, capturedBatchSize(30, 7));
        assertEquals(7, capturedLookbackDays(0));
        assertEquals(3, capturedLookbackDays(3));
    }

    @Test
    void recalculationFailureKeepsPendingAndNeverPays() {
        stubPending(order("GT1"));
        doThrow(new IllegalStateException("离线码地铁票价查询失败"))
                .when(fareCalculator).calculateOfflineFare(any(), any());

        assertEquals(0, service().recoverOfflineFarePendingOrders(50, 7));
        verify(writer).markOfflineFarePending(eq("GT1"), eq("20260913"), contains("离线码金额重算仍失败"));
        verify(writer, never()).applyOfflineFareRecalculated(any());
        verifyNoInteractions(paySignClient);
    }

    @Test
    void zeroRecalculatedAmountNeverSettlesAsSuccess() {
        stubPending(order("GT2"));
        stubCalculated(0, 0);

        assertEquals(0, service().recoverOfflineFarePendingOrders(50, 7));
        verify(writer).markOfflineFarePending(eq("GT2"), eq("20260913"), contains("重算金额为 0"));
        verify(writer, never()).applyOfflineFareRecalculated(any());
        verifyNoInteractions(paySignClient);
    }

    @Test
    void losingTheCasRaceNeverTriggersPayment() {
        stubPending(order("GT3"));
        stubCalculated(400, 0);
        when(writer.applyOfflineFareRecalculated(any())).thenReturn(0);

        assertEquals(0, service().recoverOfflineFarePendingOrders(50, 7));
        verifyNoInteractions(paySignClient);
    }

    @Test
    void winningTheCasRacePaysWithTrxPlusOvertime() {
        GateTxnPay pending = order("GT4");
        stubPending(pending);
        stubCalculated(400, 300);
        when(writer.applyOfflineFareRecalculated(any())).thenReturn(1);

        assertEquals(1, service().recoverOfflineFarePendingOrders(50, 7));
        assertEquals(700, pending.getTotalAmount(), "TOTAL_AMOUNT MUST = 实扣 400 + 超时费 300");
        verify(paySignClient).requestPay(any());
    }

    @Test
    void oneRowFailureNeverAbortsTheBatch() {
        when(mapper.selectOfflineFarePending(anyString(), anyString(), anyInt()))
                .thenReturn(List.of(order("GT5"), order("GT6"), order("GT7")));
        stubCalculated(400, 0);
        when(writer.applyOfflineFareRecalculated(any())).thenReturn(1);
        doThrow(new IllegalStateException("ORA-00060 死锁"))
                .when(writer).applyOfflineFareRecalculated(argThatOrderNoIs("GT6"));

        assertEquals(2, service().recoverOfflineFarePendingOrders(50, 7), "失败那笔不计入，其余两笔 MUST 推进");
        verify(paySignClient, org.mockito.Mockito.times(2)).requestPay(any());
    }

    private GateTxnPay argThatOrderNoIs(String orderNo) {
        return org.mockito.ArgumentMatchers.argThat(o -> o != null && orderNo.equals(o.getOrderNo()));
    }

    private int capturedBatchSize(int limit, int lookbackDays) {
        GateTxnPayMapper fresh = mock(GateTxnPayMapper.class);
        when(fresh.selectOfflineFarePending(anyString(), anyString(), anyInt())).thenReturn(List.of());
        service(fresh).recoverOfflineFarePendingOrders(limit, lookbackDays);
        ArgumentCaptor<Integer> size = ArgumentCaptor.forClass(Integer.class);
        verify(fresh).selectOfflineFarePending(anyString(), anyString(), size.capture());
        return size.getValue();
    }

    private long capturedLookbackDays(int lookbackDays) {
        GateTxnPayMapper fresh = mock(GateTxnPayMapper.class);
        when(fresh.selectOfflineFarePending(anyString(), anyString(), anyInt())).thenReturn(List.of());
        service(fresh).recoverOfflineFarePendingOrders(50, lookbackDays);
        ArgumentCaptor<String> start = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> end = ArgumentCaptor.forClass(String.class);
        verify(fresh).selectOfflineFarePending(start.capture(), end.capture(), anyInt());
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.BASIC_ISO_DATE;
        return java.time.temporal.ChronoUnit.DAYS.between(
                java.time.LocalDate.parse(start.getValue(), fmt), java.time.LocalDate.parse(end.getValue(), fmt));
    }

    private void stubPending(GateTxnPay pending) {
        when(mapper.selectOfflineFarePending(anyString(), anyString(), anyInt())).thenReturn(List.of(pending));
    }

    private void stubCalculated(int trxAmount, int overtimeAmount) {
        doAnswer(invocation -> {
            GateTxnPay order = invocation.getArgument(0);
            order.setTrxAmount(trxAmount);
            order.setOvertimeAmount(overtimeAmount);
            return null;
        }).when(fareCalculator).calculateOfflineFare(any(), any());
    }

    private GateTxnPay order(String orderNo) {
        GateTxnPay order = new GateTxnPay();
        order.setOrderNo(orderNo);
        order.setTxnDate("20260913");
        order.setDebitStatus("INIT");
        order.setTrxAmount(0);
        order.setOvertimeAmount(0);
        order.setTotalAmount(0);
        order.setCardId("C1");
        order.setCardType("04");
        order.setThirdUserId("U1");
        order.setTrxType("02");
        order.setInStation("0101");
        order.setOutStation("0110");
        order.setOfflineFlag("Y");
        order.setPaymentVendor("0B");
        return order;
    }

    private OfflineFareRecoveryServiceImpl service() {
        return service(mapper);
    }

    private OfflineFareRecoveryServiceImpl service(GateTxnPayMapper gateTxnPayMapper) {
        return new OfflineFareRecoveryServiceImpl(
                gateTxnPayMapper, writer, fareCalculator,
                new PaySignInitiator(writer,
                        new PayInitiationRpcAdapter(paySignClient, alipayPaySignClient),
                        new GatePayRequestFactory("AGM_GATE", "1", "地铁乘车扣费", "地铁乘车费用", 60L),
                        new AlipayTripPayRequestFactory("TRIP", "05", "1", "地铁乘车扣费", "地铁乘车费用", 60)),
                new MetroTransferPushTaskProcessor(null, null, false, 0, 0, 0L),
                noopStationNameBackfiller());
    }


    private StationNameBackfiller noopStationNameBackfiller() {
        return new StationNameBackfiller(new FareDataGateway(null, null, null, null, null, null) {
            @Override
            public Map<String, String> resolveStationNamesQuietly(Set<String> stationCodes) {
                return Collections.emptyMap();
            }
        });
    }
}
