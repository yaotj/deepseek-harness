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

/** 钉住离线码金额补偿（{@code recoverOfflineFarePendingOrders}）的资损防线。 */
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

    /** 单轮上限钳制：`limit<=0` 落 50、超 200 收到 200，回溯天数 `<=0` 落 7 天。 */
    @Test
    void batchSizeAndLookbackAreClamped() {
        assertEquals(50, capturedBatchSize(0, 7));
        assertEquals(50, capturedBatchSize(-1, 7));
        assertEquals(200, capturedBatchSize(9999, 7));
        assertEquals(30, capturedBatchSize(30, 7));
        assertEquals(7, capturedLookbackDays(0));
        assertEquals(3, capturedLookbackDays(3));
    }

    /** 重算仍失败 → 只调 {@code markOfflineFarePending} 保持待重算态。 */
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

    /** 重算出 0 元 → 保持待重算等人工核查。 */
    @Test
    void zeroRecalculatedAmountNeverSettlesAsSuccess() {
        stubPending(order("GT2"));
        stubCalculated(0, 0);

        assertEquals(0, service().recoverOfflineFarePendingOrders(50, 7));
        verify(writer).markOfflineFarePending(eq("GT2"), eq("20260913"), contains("重算金额为 0"));
        verify(writer, never()).applyOfflineFareRecalculated(any());
        verifyNoInteractions(paySignClient);
    }

    /** CAS 抢占失败（{@code applyOfflineFareRecalculated ! */
    @Test
    void losingTheCasRaceNeverTriggersPayment() {
        stubPending(order("GT3"));
        stubCalculated(400, 0);
        when(writer.applyOfflineFareRecalculated(any())).thenReturn(0);

        assertEquals(0, service().recoverOfflineFarePendingOrders(50, 7));
        verifyNoInteractions(paySignClient);
    }

    /** 抢占成功才扣款。 */
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

    /** 回溯天数只能从「起止日期相差几天」反推——它不是参数，而是被算进扫描区间的。 */
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

    /** 让算价 mock 按真实实现那样写回订单对象：`calculateOfflineFare` 是 void，金额靠副作用落在 `order` 上。 */
    private void stubCalculated(int trxAmount, int overtimeAmount) {
        doAnswer(invocation -> {
            GateTxnPay order = invocation.getArgument(0);
            order.setTrxAmount(trxAmount);
            order.setOvertimeAmount(overtimeAmount);
            return null;
        }).when(fareCalculator).calculateOfflineFare(any(), any());
    }

    /** 待重算行的形态：`DEBIT_STATUS='INIT'` + 金额全 0，但不是免扣费交易。 */
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

    /** 补偿链路的五个协作者：mapper（扫表）、writer（抢占与标记）、算价、扣款、换乘推送任务构建。 */
    private OfflineFareRecoveryServiceImpl service(GateTxnPayMapper gateTxnPayMapper) {
        return new OfflineFareRecoveryServiceImpl(
                gateTxnPayMapper, writer, fareCalculator,
                new PaySignInitiator(paySignClient, alipayPaySignClient, writer,
                        new GatePayRequestFactory("AGM_GATE", "1", "地铁乘车扣费", "地铁乘车费用", 60L),
                        new AlipayTripPayRequestFactory("TRIP", "05", "1", "地铁乘车扣费", "地铁乘车费用", 60)),
                new MetroTransferPushTaskProcessor(null, null, false, 0, 0, 0L),
                noopStationNameBackfiller());
    }

    /**
     * 站名回填在本文件里不能传 null：{@code recoverSingleOfflineFareOrder} 重算成功后会调它，传 null 直接 NPE、被单笔 catch 吞掉。
     */
    private StationNameBackfiller noopStationNameBackfiller() {
        return new StationNameBackfiller(new FareDataGateway(null, null, null, null, null, null) {
            @Override
            public Map<String, String> resolveStationNamesQuietly(Set<String> stationCodes) {
                return Collections.emptyMap();
            }
        });
    }
}
