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

/**
 * 钉住离线码金额补偿（{@code recoverOfflineFarePendingOrders}）的**资损防线**。
 *
 * <p>这条链路的危险性不在算错钱，而在**收口错状态**：待重算行是
 * {@code DEBIT_STATUS='INIT'} + {@code TOTAL_AMOUNT=0} 但**并非免扣费交易**。
 * 一旦被错误置成 SUCCESS，那笔车费就永久收不回来；置成 FAIL 则补偿再也捞不到它。
 * 因此本文件的每条断言对应一条 NEVER：</p>
 * <ul>
 *   <li>重算失败 → 只标回待重算，**NEVER** 推进状态、**NEVER** 发起扣款；</li>
 *   <li>重算金额为 0 → 同上，**NEVER** 按 0 元收口成功；</li>
 *   <li>CAS 抢占失败（另一副本已处理）→ **NEVER** 再扣一次；</li>
 *   <li>单笔异常 → **NEVER** 中断整批，剩下的行都是资损口。</li>
 * </ul>
 *
 * <p>抽成独立协作者后<b>断言值 NEVER 改</b>：断言不变才是行为没变的证据。</p>
 */
class OfflineFareRecoveryTest {

    private final GateTxnPayMapper mapper = mock(GateTxnPayMapper.class);
    private final GateTxnPayWriter writer = mock(GateTxnPayWriter.class);
    private final FareCalculator fareCalculator = mock(FareCalculator.class);
    private final PaySignClient paySignClient = mock(PaySignClient.class);
    // 本文件的用例全是非支付宝渠道（ISSUE_CHANNEL_CODE 不是 07），支付宝分支永远走不到，
    // 这个 mock 只为满足构造器；断言支付宝分派 MUST 另写用例，NEVER 靠这里的 mock 冒充覆盖。
    private final AlipayPaySignClient alipayPaySignClient = mock(AlipayPaySignClient.class);

    /** 空结果集直接返 0：**NEVER** 在没有待重算行时还去写库。 */
    @Test
    void emptyBatchWritesNothing() {
        when(mapper.selectOfflineFarePending(anyString(), anyString(), anyInt())).thenReturn(List.of());

        assertEquals(0, service().recoverOfflineFarePendingOrders(50, 7));
        verifyNoInteractions(writer);
        verifyNoInteractions(fareCalculator);
        verifyNoInteractions(paySignClient);
    }

    /** 单轮上限钳制：`limit<=0` 落 50、超 200 收到 200；回溯天数 `<=0` 落 7 天。 */
    @Test
    void batchSizeAndLookbackAreClamped() {
        assertEquals(50, capturedBatchSize(0, 7));
        assertEquals(50, capturedBatchSize(-1, 7));
        assertEquals(200, capturedBatchSize(9999, 7));
        assertEquals(30, capturedBatchSize(30, 7));
        assertEquals(7, capturedLookbackDays(0));
        assertEquals(3, capturedLookbackDays(3));
    }

    /**
     * 重算仍失败 → 只调 {@code markOfflineFarePending} 保持待重算态。
     *
     * <p>**NEVER** 置 FAIL（补偿再也捞不到这笔）也 **NEVER** 置 SUCCESS（资损）；
     * 更不能发起扣款——金额根本没算出来。</p>
     */
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

    /**
     * 重算出 0 元 → 保持待重算等人工核查。
     *
     * <p>这是最隐蔽的一条：票价参数异常时重算会「成功」返回 0，若按 0 元收口成 SUCCESS，
     * 账面完全正常、对账也不报错，**车费永久收不回来**。</p>
     */
    @Test
    void zeroRecalculatedAmountNeverSettlesAsSuccess() {
        stubPending(order("GT2"));
        stubCalculated(0, 0);

        assertEquals(0, service().recoverOfflineFarePendingOrders(50, 7));
        verify(writer).markOfflineFarePending(eq("GT2"), eq("20260913"), contains("重算金额为 0"));
        verify(writer, never()).applyOfflineFareRecalculated(any());
        verifyNoInteractions(paySignClient);
    }

    /**
     * CAS 抢占失败（{@code applyOfflineFareRecalculated != 1}）→ 立即收手。
     *
     * <p>返回 0 意味着另一个副本已经处理了这笔。此时继续调 pay-sign 就是**重复扣款**，
     * 所以顺序 MUST 是「先抢占、再扣款」，且返回值 MUST 被检查。</p>
     */
    @Test
    void losingTheCasRaceNeverTriggersPayment() {
        stubPending(order("GT3"));
        stubCalculated(400, 0);
        when(writer.applyOfflineFareRecalculated(any())).thenReturn(0);

        assertEquals(0, service().recoverOfflineFarePendingOrders(50, 7));
        verifyNoInteractions(paySignClient);
    }

    /** 抢占成功才扣款，且 {@code TOTAL_AMOUNT} MUST 等于「实扣 + 超时费」。 */
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

    /**
     * 单笔异常 **NEVER** 中断整批。
     *
     * <p>改造前循环体是裸调用，中间那笔一抛就冲出 for，本轮剩余待重算订单全部不处理 ——
     * 而它们每一笔都是 {@code TOTAL_AMOUNT=0} 的资损口。这里让第 2 笔在抢占时抛异常，
     * 断言第 3 笔照样被扣款、返回值只计成功笔数。</p>
     */
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

    /**
     * 让算价 mock 按真实实现那样**写回订单对象**：`calculateOfflineFare` 是 void，
     * 金额靠副作用落在 `order` 上，`recoverSingleOfflineFareOrder` 随后据此算 TOTAL_AMOUNT。
     */
    private void stubCalculated(int trxAmount, int overtimeAmount) {
        doAnswer(invocation -> {
            GateTxnPay order = invocation.getArgument(0);
            order.setTrxAmount(trxAmount);
            order.setOvertimeAmount(overtimeAmount);
            return null;
        }).when(fareCalculator).calculateOfflineFare(any(), any());
    }

    /** 待重算行的形态：`DEBIT_STATUS='INIT'` + 金额全 0，但**不是**免扣费交易。 */
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

    /**
     * 补偿链路的五个协作者：mapper（扫表）、writer（抢占与标记）、算价、扣款、换乘推送任务构建。
     *
     * <p>{@link PaySignInitiator} 与 {@link MetroTransferPushTaskProcessor} 传**真实实例**而非 mock：
     * 断言钉的是「有没有真的调 pay-sign」，mock 掉它们就等于把被测的那条线剪断了。
     * 两者内部只依赖同一组 {@code paySignClient} / {@code writer}（换乘构建更是纯函数），
     * 因此这段从 {@code GateTxnPayServiceImpl} 抽成独立服务后**断言一行没改**。</p>
     */
    private OfflineFareRecoveryServiceImpl service(GateTxnPayMapper gateTxnPayMapper) {
        return new OfflineFareRecoveryServiceImpl(
                gateTxnPayMapper, writer, fareCalculator,
                new PaySignInitiator(paySignClient, alipayPaySignClient, writer,
                        new GatePayRequestFactory("AGM_GATE", "1", "地铁乘车扣费", "地铁乘车费用", 60L),
                        new AlipayTripPayRequestFactory("TRIP", "05", "1", "地铁乘车扣费", "地铁乘车费用", 60,
                                "http://localhost/payNotify")),
                new MetroTransferPushTaskProcessor(null, null, false, 0, 0, 0L),
                noopStationNameBackfiller());
    }

    /**
     * 站名回填在本文件里**不能传 null**：{@code recoverSingleOfflineFareOrder} 重算成功后会调它，
     * 传 null 直接 NPE、被单笔 catch 吞掉，于是本该断言的「抢占 + 扣款」根本没执行到
     * （与第 6 位 {@code metroTransferPushTaskProcessor} 同款陷阱）。
     * 这里给的是「一个站名都查不到」的桩：本文件断言的是金额与状态，站名不在断言范围内，
     * 让它恒返空 Map 即可保持既有断言值一行不改。
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
