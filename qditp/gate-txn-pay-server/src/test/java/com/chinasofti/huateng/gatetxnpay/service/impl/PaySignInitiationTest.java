package com.chinasofti.huateng.gatetxnpay.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
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
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestPayReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestPayRespDTO;
import com.chinasofti.huateng.model.app.GatePayRequestDTO;
import com.chinasofti.huateng.model.app.RequestPayResult;
import com.chinasofti.huateng.rpc.alipay.paysign.AlipayPaySignClient;
import com.chinasofti.huateng.rpc.paySign.PaySignClient;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 钉住「发起扣款」这一段的报文与状态收敛口径。
 *
 * <p>这是出站扣费的**唯一出账口**，`requestPay` / `retryPay` / 离线码补偿三条链路共用它。
 * 断言分两类：</p>
 * <ul>
 *   <li><b>报文口径</b>：{@code TXN_DATE} MUST 取订单快照的交易日（出站日），**NEVER** 取当日 ——
 *       两表按 {@code (ORDER_NO, TXN_DATE)} 关联且都以它做月分区，出站到落库实测滞后达 94 分钟，
 *       22:26 之后出站时若支付域自己取 {@code now()} 就跨日、关联即落空；金额 MUST 是
 *       {@code TOTAL_AMOUNT}（实扣 + 超时费），**NEVER** 只发 {@code TRX_AMOUNT}。</li>
 *   <li><b>状态收敛</b>：只有 {@code retCode=0000} 才推进 PROCESSING，其余一切（非 0000 / null /
 *       抛异常）一律落 RETRY 等补偿。**NEVER** 把「没抛异常」当成扣款成功
 *       （AGENTS.md §5.2「返回 boolean 的 RPC 包装方法」同型陷阱）。</li>
 * </ul>
 *
 * <p>断言全部走 {@code retryPay} / {@code recoverOfflineFarePendingOrders} 两个公开入口，
 * 因此把这段抽成独立协作者后<b>断言值 NEVER 改</b> —— 断言不变才是行为没变的证据。</p>
 */
class PaySignInitiationTest {

    /** 出站日：故意取一个远离「今天」的值，`now()` 一旦混进报文立刻被 {@link #txnDateComesFromOrderNeverToday} 抓到。 */
    private static final String OUT_TXN_DATE = "20260101";

    /**
     * 落单时由 fep-dev-server 透传存进 `INDUSTRY_DETAIL` 的那份 JSON。
     *
     * <p>这里只取三个键做标记，够用即可 —— 断言的是「原样透传」，不是键的完整性；
     * 用订单快照重算出来的 JSON 键名是 {@code orderNo} / {@code cardId} 那一套，与这三个键不可能相等。</p>
     */
    private static final String INDUSTRY_DETAIL =
            "{\"entryLineCode\":\"L1\",\"entryId\":\"E1\",\"cardIssueCode\":\"0007\"}";

    private final GateTxnPayMapper mapper = mock(GateTxnPayMapper.class);
    private final GateTxnPayWriter writer = mock(GateTxnPayWriter.class);
    private final FareCalculator fareCalculator = mock(FareCalculator.class);
    private final PaySignClient paySignClient = mock(PaySignClient.class);
    // 本文件的用例全是非支付宝渠道（ISSUE_CHANNEL_CODE 不是 07），支付宝分支永远走不到，
    // 这个 mock 只为满足构造器；断言支付宝分派 MUST 另写用例，NEVER 靠这里的 mock 冒充覆盖。
    private final AlipayPaySignClient alipayPaySignClient = mock(AlipayPaySignClient.class);

    /**
     * {@code TXN_DATE} MUST 等于订单快照的交易日，**NEVER** 等于当日。
     *
     * <p>这是本文件里唯一「错了不报错、只是对不上账」的不变量：取错值时报文照样发出去、
     * pay-sign 照样返 0000，只有跨日那一小时的订单在 {@code PAY_TXN_DETAIL} 上关联不到，
     * 事后只能靠人工核对发现。</p>
     */
    @Test
    void txnDateComesFromOrderNeverToday() {
        stubRetryable(order("GT10", 400, 0));
        when(paySignClient.requestPay(any())).thenReturn(payResult("0000"));

        service().retryPay("GT10");

        assertEquals(OUT_TXN_DATE, capturedRequest().getTxnDate(), "TXN_DATE MUST 取订单的出站日");
        assertNotEquals(LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE),
                capturedRequest().getTxnDate(), "TXN_DATE NEVER 取当日");
    }

    /** 上送金额 MUST 是 {@code TOTAL_AMOUNT}（实扣 + 超时费），漏掉超时费就是少收钱。 */
    @Test
    void amountIsTotalAmountIncludingOvertimeFee() {
        stubRetryable(order("GT11", 400, 300));
        when(paySignClient.requestPay(any())).thenReturn(payResult("0000"));

        service().retryPay("GT11");

        assertEquals(700, capturedRequest().getAmount(), "MUST = 实扣 400 + 超时费 300");
    }

    /** 场景 / 行业类型 / 商品标题 / 订单超时全部来自配置项，报文里一个都不能少。 */
    @Test
    void configuredSceneAndTimeoutAreCarriedIntoTheRequest() {
        stubRetryable(order("GT12", 400, 0));
        when(paySignClient.requestPay(any())).thenReturn(payResult("0000"));

        service().retryPay("GT12");

        GatePayRequestDTO sent = capturedRequest();
        assertEquals("AGM_GATE", sent.getScene());
        assertEquals("1", sent.getIndustryType());
        assertEquals("地铁乘车扣费", sent.getSubject());
        assertEquals("地铁乘车费用", sent.getBody());
        assertEquals(60L, sent.getOrderTimeOut());
        assertEquals("GT12", sent.getOrderNo());
    }

    /** {@code retCode=0000} 才推进 PROCESSING。 */
    @Test
    void successRetCodeAdvancesToProcessing() {
        stubRetryable(order("GT13", 400, 0));
        when(paySignClient.requestPay(any())).thenReturn(payResult("0000"));

        assertEquals("PROCESSING", service().retryPay("GT13").getPayStatus());
        verify(writer).updateOrderStatusFromPending(any(), eq("PROCESSING"), any());
    }

    /**
     * 非 0000 一律落 RETRY。
     *
     * <p>**NEVER** 写成「非 FAIL 即成功」：pay-sign 返任何非 0000 都意味着这笔钱没扣到，
     * 推进成 PROCESSING 会让补偿再也不碰它。</p>
     */
    @Test
    void nonSuccessRetCodeFallsBackToRetry() {
        stubRetryable(order("GT14", 400, 0));
        when(paySignClient.requestPay(any())).thenReturn(payResult("9999"));

        assertEquals("RETRY", service().retryPay("GT14").getPayStatus());
        verify(writer).updateOrderStatusFromPending(any(), eq("RETRY"), any());
    }

    /** 返回 null（连接不上 / 报文解析失败）同样落 RETRY，且原因 MUST 留痕。 */
    @Test
    void nullResponseFallsBackToRetryWithReason() {
        stubRetryable(order("GT15", 400, 0));
        when(paySignClient.requestPay(any())).thenReturn(null);

        assertEquals("RETRY", service().retryPay("GT15").getPayStatus());
        verify(writer).updateOrderStatusFromPending(any(), eq("RETRY"), contains("重试调用pay-sign失败"));
    }

    /**
     * 补偿链路（异步入口）上 pay-sign 抛异常 → 落 RETRY 且异常信息进备注。
     *
     * <p>**NEVER** 让异常冲出去：那会让已经抢占成功的这笔订单既没扣款、也没留下重试标记。</p>
     */
    @Test
    void paySignExceptionIsRecordedAsRetry() {
        stubPending(pendingOrder("GT16"));
        stubCalculated(400, 0);
        when(writer.applyOfflineFareRecalculated(any())).thenReturn(1);
        when(paySignClient.requestPay(any())).thenThrow(new IllegalStateException("Connection refused"));

        assertEquals(1, recoveryService().recoverOfflineFarePendingOrders(50, 7), "异常 NEVER 让这笔算作未推进");
        verify(writer).updateOrderStatusFromPending(any(), eq("RETRY"), contains("Connection refused"));
    }

    /**
     * {@code ISSUE_CHANNEL_CODE=07} MUST 走 alipay-pay-sign，**NEVER** 走支付中心。
     *
     * <p>分派错了不会报错：pay-sign 收到一笔它查不到签约的订单，返非 0000、订单落 RETRY，
     * 看起来只是「扣费失败」，实际是整条支付宝出行链路全部打不通。</p>
     */
    @Test
    void alipayChannelGoesToAlipayNeverToPaySign() {
        stubRetryable(alipayOrder("GT20"));
        when(alipayPaySignClient.alipayTripRequestPay(any())).thenReturn(alipayPayResult("0000"));

        service().retryPay("GT20");

        verify(alipayPaySignClient).alipayTripRequestPay(any());
        verifyNoInteractions(paySignClient);
        verify(writer).updateOrderStatusFromPending(any(), eq("PROCESSING"), anyString());
    }

    /**
     * 支付宝报文的 {@code industryDetail} MUST 是落单时存下的那份 21 键 JSON 原样透传。
     *
     * <p>其中 9 个键（进出站线路码 / 名称、进站设备号、entryId / exitId、cardNum、cardIssueCode）
     * 在 `GATE_TXN_PAY` 没有列，只有出站那一刻 fep-dev-server 的三个并行 RPC 拿得到；
     * 谁把这里改成按订单快照重算，得到的是键名完全不同的 JSON，支付宝解析不出行程、扣费必失败。</p>
     *
     * <p>同时钉住另外两项来源：订单号取 {@code GATE_TXN_PAY.ORDER_NO}（合并的目的就是一单一号），
     * {@code requestSignSeq} 取 {@code TICKET_TRANS_SEQ}（支付宝按票卡流水号找协议）。</p>
     */
    @Test
    void alipayIndustryDetailIsPassedThroughNeverRebuilt() {
        GateTxnPay order = alipayOrder("GT21");
        stubRetryable(order);
        when(alipayPaySignClient.alipayTripRequestPay(any())).thenReturn(alipayPayResult("0000"));

        service().retryPay("GT21");

        ArgumentCaptor<AlipayTripRequestPayReqDTO> sent =
                ArgumentCaptor.forClass(AlipayTripRequestPayReqDTO.class);
        verify(alipayPaySignClient).alipayTripRequestPay(sent.capture());
        assertEquals(INDUSTRY_DETAIL, sent.getValue().getIndustryDetail(), "industryDetail MUST 原样透传，NEVER 重算");
        assertEquals("GT21", sent.getValue().getOrderNo(), "订单号 MUST 取 GATE_TXN_PAY.ORDER_NO");
        assertEquals("T21", sent.getValue().getRequestSignSeq(), "requestSignSeq MUST 取 TICKET_TRANS_SEQ");
        assertEquals(700, sent.getValue().getAmount(), "金额 MUST 是 TOTAL_AMOUNT（实扣 + 超时费）");
    }

    /** 支付宝返非 0000 时 MUST 落 RETRY —— 与 pay-sign 分支同一条收敛规则。 */
    @Test
    void alipayNonSuccessRetCodeIsRecordedAsRetry() {
        stubRetryable(alipayOrder("GT22"));
        when(alipayPaySignClient.alipayTripRequestPay(any())).thenReturn(alipayPayResult("9999"));

        service().retryPay("GT22");

        verify(writer).updateOrderStatusFromPending(any(), eq("RETRY"), anyString());
    }

    /** 非支付宝渠道 **NEVER** 碰 alipay-pay-sign：分派是双向的，反向错了同样打不通。 */
    @Test
    void nonAlipayChannelNeverTouchesAlipay() {
        stubRetryable(order("GT23", 400, 0));
        when(paySignClient.requestPay(any())).thenReturn(payResult("0000"));

        service().retryPay("GT23");

        verifyNoInteractions(alipayPaySignClient);
    }

    private AlipayTripRequestPayRespDTO alipayPayResult(String retCode) {
        AlipayTripRequestPayRespDTO result = new AlipayTripRequestPayRespDTO();
        result.setRetCode(retCode);
        result.setRetMsg("retCode=" + retCode);
        return result;
    }

    /** 支付宝出行的可重试订单：`ISSUE_CHANNEL_CODE=07` + 落单时透传存下的 industryDetail。 */
    private GateTxnPay alipayOrder(String orderNo) {
        GateTxnPay order = order(orderNo, 400, 300);
        order.setIssueChannelCode("07");
        order.setTicketTransSeq("T" + orderNo.substring(2));
        order.setIndustryDetail(INDUSTRY_DETAIL);
        return order;
    }

    private GatePayRequestDTO capturedRequest() {
        ArgumentCaptor<GatePayRequestDTO> sent = ArgumentCaptor.forClass(GatePayRequestDTO.class);
        verify(paySignClient).requestPay(sent.capture());
        return sent.getValue();
    }

    private RequestPayResult payResult(String retCode) {
        RequestPayResult result = new RequestPayResult();
        result.setRetCode(retCode);
        result.setRetMsg("retCode=" + retCode);
        return result;
    }

    /** `retryPay` 的前置：订单存在、状态在白名单内。 */
    private void stubRetryable(GateTxnPay order) {
        when(mapper.selectByOrderNo(order.getOrderNo())).thenReturn(order);
    }

    private void stubPending(GateTxnPay pending) {
        when(mapper.selectOfflineFarePending(anyString(), anyString(), anyInt())).thenReturn(List.of(pending));
    }

    /** 算价是 void，金额靠副作用写回订单对象。 */
    private void stubCalculated(int trxAmount, int overtimeAmount) {
        doAnswer(invocation -> {
            GateTxnPay order = invocation.getArgument(0);
            order.setTrxAmount(trxAmount);
            order.setOvertimeAmount(overtimeAmount);
            return null;
        }).when(fareCalculator).calculateOfflineFare(any(), any());
    }

    /** 可重试的失败订单：`DEBIT_STATUS='RETRY'`，金额已算出。 */
    private GateTxnPay order(String orderNo, int trxAmount, int overtimeAmount) {
        GateTxnPay order = base(orderNo);
        order.setDebitStatus("RETRY");
        order.setTrxAmount(trxAmount);
        order.setOvertimeAmount(overtimeAmount);
        order.setTotalAmount(trxAmount + overtimeAmount);
        return order;
    }

    /** 待重算行：`INIT` + 金额全 0，但**不是**免扣费交易。 */
    private GateTxnPay pendingOrder(String orderNo) {
        GateTxnPay order = base(orderNo);
        order.setDebitStatus("INIT");
        order.setTrxAmount(0);
        order.setOvertimeAmount(0);
        order.setTotalAmount(0);
        order.setOfflineFlag("Y");
        return order;
    }

    private GateTxnPay base(String orderNo) {
        GateTxnPay order = new GateTxnPay();
        order.setOrderNo(orderNo);
        order.setTxnDate(OUT_TXN_DATE);
        order.setCardId("C1");
        order.setCardType("04");
        order.setThirdUserId("U1");
        order.setTrxType("02");
        order.setInStation("0101");
        order.setOutStation("0110");
        order.setPaymentVendor("0B");
        order.setPayUserId("P1");
        return order;
    }

    /**
     * 扣款链路用到的协作者：mapper（回查订单）、writer（状态收敛）、算价、pay-sign、补款单前置。
     * 异步执行器传 null —— 本文件走的两条入口都是同步调用扣款，谁把它改成异步这里立刻 NPE。
     *
     * <p>{@link PaySignInitiator} 传**真实实例**：本文件断言的正是它组的报文与它收敛的状态，
     * mock 掉就什么都没测到。</p>
     *
     * <p>第 6 位 {@code metroTransferPushTaskProcessor} **不能传 null**：
     * {@link #paySignExceptionIsRecordedAsRetry} 走的是补偿入口，抢占成功后会先建换乘推送任务，
     * 传 null 会在那一步 NPE、被单笔 catch 吞掉，于是本该断言的「落 RETRY」根本没执行到
     * （表现为 {@code expected: <1> but was: <0>}，而非直接报 NPE —— 已实测）。
     * 最后一位是 {@code paySignAsyncExecutor}。<b>构造器增删参数时 MUST 同步这里的占位数量</b>。</p>
     */
    private GateTxnPayServiceImpl service() {
        return new GateTxnPayServiceImpl(
                mapper, writer, fareCalculator, initiator(),
                new MetroTransferPushTaskProcessor(null, null, false, 0, 0, 0L),
                new GateTxnPayManualOpsService(mapper, paySignClient, initiator()),
                noopStationNameBackfiller(),
                null);
    }

    /**
     * 站名回填 **NEVER 传 null**：`requestPay` 与补偿链路都会调它，null 直接 NPE
     * （补偿侧还会被单笔 catch 吞掉，表现成断言值对不上而不是 NPE，与第 5 位
     * {@code metroTransferPushTaskProcessor} 同款陷阱）。
     * 桩恒返空 Map（「一个站名都查不到」），因此本文件的报文断言值一行不用改。
     */
    private StationNameBackfiller noopStationNameBackfiller() {
        return new StationNameBackfiller(new FareDataGateway(null, null, null, null, null, null) {
            @Override
            public Map<String, String> resolveStationNamesQuietly(Set<String> stationCodes) {
                return Collections.emptyMap();
            }
        });
    }

    /**
     * 两个渠道的报文工厂传**真实实例**：本文件断言的正是它们组出来的报文字段，mock 掉就什么都没测到。
     *
     * <p>参数分两组、单位不同（pay-sign 的 60 是**秒**、支付宝的 60 是**分钟**），
     * <b>NEVER 把两组合并成一串</b>。</p>
     */
    private PaySignInitiator initiator() {
        return new PaySignInitiator(paySignClient, alipayPaySignClient, writer,
                new GatePayRequestFactory("AGM_GATE", "1", "地铁乘车扣费", "地铁乘车费用", 60L),
                new AlipayTripPayRequestFactory("TRIP", "05", "1", "地铁乘车扣费", "地铁乘车费用", 60,
                        "http://localhost/payNotify"));
    }

    /**
     * 补偿入口已搬到 {@link OfflineFareRecoveryServiceImpl}，但**异步扣款的收敛口径仍由本文件钉住**：
     * {@link #paySignExceptionIsRecordedAsRetry} 断言的是「pay-sign 抛异常 → 落 RETRY 且异常进备注」，
     * 那条路只有补偿链路会同步走到，因此这里装配的是补偿服务而不是订单服务。
     * 两者共用同一个真实 {@link PaySignInitiator}，所以搬家后**断言值一行没改**。
     */
    private OfflineFareRecoveryServiceImpl recoveryService() {
        return new OfflineFareRecoveryServiceImpl(
                mapper, writer, fareCalculator, initiator(),
                new MetroTransferPushTaskProcessor(null, null, false, 0, 0, 0L),
                noopStationNameBackfiller());
    }
}
