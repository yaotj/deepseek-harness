package com.chinasofti.huateng.facepay.service;

import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterClient;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterMessageFactory;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterRequest;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterResult;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterResults;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterStatus;
import com.chinasofti.huateng.facepay.domain.F2fOrderStatus;
import com.chinasofti.huateng.facepay.mapper.F2fOrderMapper;
import com.chinasofti.huateng.facepay.mapper.F2fPaymentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 锁死 {@link F2fPayCenterFlow} 的收口语义。
 *
 * <p><b>为什么这批用例必须存在</b>：重构前这条骨架在 7 个 service 里各抄一遍，
 * 而模块里唯一覆盖过它的 {@code F2fTvmOrderServiceWriteTest} 是 {@code @Disabled}（要真库）。
 * 也就是说「支付中心答复 → 本地状态推进」这一步在重构前<b>没有任何自动化验证</b>。
 * 收口成一个类之后，这些不变量终于可以用 mock 钉住。</p>
 *
 * <p>每个用例对应一条<b>踩过或差点踩到的坑</b>，注释里写明了是哪一条，NEVER 删。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class F2fPayCenterFlowTest {

    private static final String ORDER_NO = "F2F20260914000001";

    private static final List<String> PENDING = F2fOrderStatus.PENDING;

    @Mock
    private PayCenterClient payCenterClient;

    @Mock
    private PayCenterMessageFactory messageFactory;

    @Mock
    private F2fPaymentMapper paymentMapper;

    @Mock
    private F2fOrderMapper orderMapper;

    @Mock
    private PayCenterRequest message;

    @Mock
    private com.chinasofti.huateng.facepay.channel.paycenter.PayCenterProperties payCenterProperties;

    @Mock
    private F2fNotifyService notifyService;

    private F2fPayCenterFlow flow;

    @BeforeEach
    void setUp() {
        flow = new F2fPayCenterFlow(payCenterClient, messageFactory, paymentMapper, orderMapper, notifyService);
        when(payCenterClient.properties()).thenReturn(payCenterProperties);
    }
    /** 受理成功：支付流水置 PROCESSING，订单尝试推 PAYING。 */
    @Test
    void acceptedMarksProcessingAndPushesPaying() {
        PayCenterResult result = answered("0", "https://qr.example/abc");
        when(payCenterClient.execute(any(), eq(message))).thenReturn(result);

        F2fPayCenterFlow.Submitted submitted = flow.submit(preOrderSpec(rejectToPayFailed()));

        assertInstanceOf(F2fPayCenterFlow.Submitted.Accepted.class, submitted);
        verify(paymentMapper).markFinalStatus(eq(ORDER_NO), eq(1), eq(List.of("INIT")), eq("PROCESSING"),
                any(), any(), anyInt(), any());
        verify(orderMapper).updateStatus(ORDER_NO, List.of(F2fOrderStatus.CREATED.name()),
                F2fOrderStatus.PAYING.name(), null);
    }

    /**
     * 传输失败：<b>订单一个字段都不许动</b>。
     *
     * <p>这是旧实现最大的坑（{@code PayCenterResult} 类注释里记着）：
     * 「不知道」被当成「没付成功」，钱可能已经扣了。</p>
     */
    @Test
    void transportFailureNeverTouchesOrder() {
        when(payCenterClient.execute(any(), eq(message))).thenReturn(transportFailed("read timeout"));

        F2fPayCenterFlow.Submitted submitted = flow.submit(preOrderSpec(rejectToPayFailed()));

        assertInstanceOf(F2fPayCenterFlow.Submitted.Unknown.class, submitted);
        verify(paymentMapper).markFinalStatus(eq(ORDER_NO), eq(1), eq(List.of("INIT")), eq("UNKNOWN"),
                any(), eq("read timeout"), anyInt(), any());
        verifyNoInteractions(orderMapper);
    }

    /**
     * {@code code=0} 但 {@code data} 是空的：按 UNKNOWN 收口，<b>不许推 PAYING</b>。
     *
     * <p>推了 PAYING 就等于对外宣称「码已经给出去了」，而实际上二维码串根本没拿到。</p>
     */
    @Test
    void blankDataIsUnknownNotAccepted() {
        when(payCenterClient.execute(any(), eq(message))).thenReturn(answered("0", "  "));

        F2fPayCenterFlow.Submitted submitted = flow.submit(preOrderSpec(rejectToPayFailed()));

        assertInstanceOf(F2fPayCenterFlow.Submitted.Unknown.class, submitted);
        verify(paymentMapper).markFinalStatus(eq(ORDER_NO), eq(1), eq(List.of("INIT")), eq("UNKNOWN"),
                any(), eq("受理成功但未返回二维码串"), anyInt(), any());
        verifyNoInteractions(orderMapper);
    }

    /** 被拒且给了 RejectTransition：订单推 PAY_FAILED。 */
    @Test
    void rejectedPushesPayFailedWhenTransitionGiven() {
        when(payCenterClient.execute(any(), eq(message))).thenReturn(answered("9999", null));
        when(orderMapper.updateStatus(anyString(), any(), anyString(), any())).thenReturn(1);

        F2fPayCenterFlow.Submitted submitted = flow.submit(preOrderSpec(rejectToPayFailed()));

        assertInstanceOf(F2fPayCenterFlow.Submitted.Rejected.class, submitted);
        verify(orderMapper).updateStatus(ORDER_NO, PENDING, F2fOrderStatus.PAY_FAILED.name(),
                "支付中心预下单失败:9999");
    }

    /**
     * 被拒但 {@code rejectTransition == null}：<b>订单必须留在 CREATED</b>。
     *
     * <p>这是 APP 侧的既有语义（换个支付通道还能再来一次），重构前它只体现为
     * 「{@code F2fAppOrderService} 里少了两行」，任何人都可能顺手「补齐」。
     * 本用例就是那两行不存在的证据，<b>NEVER 删</b>。</p>
     */
    @Test
    void rejectedKeepsOrderWhenTransitionAbsent() {
        when(payCenterClient.execute(any(), eq(message))).thenReturn(answered("9999", null));

        F2fPayCenterFlow.Submitted submitted = flow.submit(preOrderSpec(null));

        assertInstanceOf(F2fPayCenterFlow.Submitted.Rejected.class, submitted);
        verify(orderMapper, never()).updateStatus(anyString(), any(), anyString(), any());
    }
    /**
     * 付款码链路开了同步状态判定、且支付中心当场答 SUCCESS：
     * 本类<b>什么都不写</b>，把落库让给调用方（各渠道的成功流水列不同）。
     */
    @Test
    void syncPaidWritesNothingAndLetsCallerPersist() {
        PayCenterResult result = answeredWith(PayCenterStatus.SUCCESS);
        when(payCenterClient.execute(any(), eq(message))).thenReturn(result);

        F2fPayCenterFlow.Submitted submitted = flow.submit(new F2fPayCenterFlow.SubmitSpec(
                ORDER_NO, 2, message, null, rejectToPayFailed(), true, "付款码支付"));

        assertInstanceOf(F2fPayCenterFlow.Submitted.SyncPaid.class, submitted);
        verifyNoInteractions(paymentMapper);
        verifyNoInteractions(orderMapper);
    }

    /**
     * 查到已收款：<b>先跑调用方的支付流水回写，再 {@code markPaid}</b>。
     *
     * <p>顺序是有意的 —— 先有成功的流水，再有 PAID 的订单，中途崩了也不会出现
     * 「订单说收了钱、流水里查不到那一笔」。</p>
     */
    @Test
    void settlePaidRunsCallbackBeforeMarkPaid() {
        PayCenterResult result = answeredWith(PayCenterStatus.SUCCESS);
        when(payCenterClient.execute(any(), any())).thenReturn(result);
        when(orderMapper.markPaid(eq(ORDER_NO), any(LocalDateTime.class))).thenReturn(1);
        AtomicInteger callbackOrder = new AtomicInteger();

        F2fPayCenterFlow.Settled settled = flow.settle(new F2fPayCenterFlow.SettleSpec(
                ORDER_NO, PENDING, r -> callbackOrder.set(1), "TVM"));

        assertEquals(F2fPayCenterFlow.Settlement.PAID, settled.settlement());
        assertEquals(1, callbackOrder.get());
        InOrder inOrder = org.mockito.Mockito.inOrder(orderMapper);
        inOrder.verify(orderMapper).markPaid(eq(ORDER_NO), any(LocalDateTime.class));
    }

    /**
     * 查询没问出结论：<b>本地一律不动状态</b>，也不许调 onPaid 回调。
     *
     * <p>调用方随后要回「支付中 / 处理中」让对方继续轮询，NEVER 回失败。</p>
     */
    @Test
    void settlePendingNeverTouchesOrderNorCallback() {
        when(payCenterClient.execute(any(), any())).thenReturn(transportFailed("connect refused"));
        AtomicInteger callbackCount = new AtomicInteger();

        F2fPayCenterFlow.Settled settled = flow.settle(new F2fPayCenterFlow.SettleSpec(
                ORDER_NO, PENDING, r -> callbackCount.incrementAndGet(), "TVM"));

        assertEquals(F2fPayCenterFlow.Settlement.PENDING, settled.settlement());
        assertEquals(0, callbackCount.get());
        verifyNoInteractions(orderMapper);
        verifyNoInteractions(paymentMapper);
    }

    /** 查到未支付：推 EXPIRED（不是 PAY_FAILED），两者是不同的终态。 */
    @Test
    void settleUnpaidPushesExpired() {
        PayCenterResult result = answeredWith(PayCenterStatus.UNPAID);
        when(payCenterClient.execute(any(), any())).thenReturn(result);
        when(orderMapper.updateStatus(anyString(), any(), anyString(), any())).thenReturn(1);

        F2fPayCenterFlow.Settled settled = flow.settle(new F2fPayCenterFlow.SettleSpec(
                ORDER_NO, PENDING, r -> { }, "TVM"));

        assertEquals(F2fPayCenterFlow.Settlement.UNPAID, settled.settlement());
        verify(orderMapper).updateStatus(ORDER_NO, PENDING, F2fOrderStatus.EXPIRED.name(), "支付中心返回未支付");
        verify(orderMapper, never()).markPaid(anyString(), any());
    }

    /** CAS 返 0 行时才回查当前状态；命中就不回查（三件套规范的一部分）。 */
    @Test
    void markPaidQueriesCurrentStatusOnlyOnConflict() {
        when(orderMapper.markPaid(eq(ORDER_NO), any(LocalDateTime.class))).thenReturn(1);
        flow.markPaidAndReport(ORDER_NO);
        verify(orderMapper, never()).selectOrderStatus(anyString());

        when(orderMapper.markPaid(eq(ORDER_NO), any(LocalDateTime.class))).thenReturn(0);
        when(orderMapper.selectOrderStatus(ORDER_NO)).thenReturn(F2fOrderStatus.EXPIRED.name());
        flow.markPaidAndReport(ORDER_NO);
        verify(orderMapper, times(1)).selectOrderStatus(ORDER_NO);
    }

    private F2fPayCenterFlow.SubmitSpec preOrderSpec(F2fPayCenterFlow.RejectTransition transition) {
        return new F2fPayCenterFlow.SubmitSpec(ORDER_NO, 1, message, "二维码串", transition, false, "拉码下单");
    }

    private F2fPayCenterFlow.RejectTransition rejectToPayFailed() {
        return new F2fPayCenterFlow.RejectTransition(PENDING, "支付中心预下单失败:");
    }

    /** 对端答上来了、不带 {@code status}（拉码 / APP 预下单的形态）。 */
    private PayCenterResult answered(String code, String data) {
        return PayCenterResults.answered(code, null, data);
    }

    /** 对端答 {@code code=0} 且带明确支付状态（付款码同步结果、查询收口的形态）。 */
    private PayCenterResult answeredWith(PayCenterStatus status) {
        return PayCenterResults.answered("0", status, null);
    }

    private PayCenterResult transportFailed(String reason) {
        return PayCenterResults.transportFailed(reason);
    }
}
