package com.chinasofti.huateng.facepay.service;

import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterClient;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterMessageFactory;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterRequest;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterResult;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterResults;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterStatus;
import com.chinasofti.huateng.facepay.domain.F2fOrderStatus;
import com.chinasofti.huateng.facepay.entity.F2fOrder;
import com.chinasofti.huateng.facepay.mapper.F2fOrderMapper;
import com.chinasofti.huateng.facepay.mapper.F2fPaymentMapper;
import com.chinasofti.huateng.facepay.support.F2fChannel;
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

    @Test
    void transportFailureNeverTouchesOrder() {
        when(payCenterClient.execute(any(), eq(message))).thenReturn(transportFailed("read timeout"));

        F2fPayCenterFlow.Submitted submitted = flow.submit(preOrderSpec(rejectToPayFailed()));

        assertInstanceOf(F2fPayCenterFlow.Submitted.Unknown.class, submitted);
        verify(paymentMapper).markFinalStatus(eq(ORDER_NO), eq(1), eq(List.of("INIT")), eq("UNKNOWN"),
                any(), eq("read timeout"), anyInt(), any());
        verifyNoInteractions(orderMapper);
    }

    @Test
    void blankDataIsUnknownNotAccepted() {
        when(payCenterClient.execute(any(), eq(message))).thenReturn(answered("0", "  "));

        F2fPayCenterFlow.Submitted submitted = flow.submit(preOrderSpec(rejectToPayFailed()));

        assertInstanceOf(F2fPayCenterFlow.Submitted.Unknown.class, submitted);
        verify(paymentMapper).markFinalStatus(eq(ORDER_NO), eq(1), eq(List.of("INIT")), eq("UNKNOWN"),
                any(), eq("受理成功但未返回二维码串"), anyInt(), any());
        verifyNoInteractions(orderMapper);
    }

    @Test
    void rejectedPushesPayFailedWhenTransitionGiven() {
        when(payCenterClient.execute(any(), eq(message))).thenReturn(answered("9999", null));
        when(orderMapper.updateStatus(anyString(), any(), anyString(), any())).thenReturn(1);

        F2fPayCenterFlow.Submitted submitted = flow.submit(preOrderSpec(rejectToPayFailed()));

        assertInstanceOf(F2fPayCenterFlow.Submitted.Rejected.class, submitted);
        verify(orderMapper).updateStatus(ORDER_NO, PENDING, F2fOrderStatus.PAY_FAILED.name(),
                "支付中心预下单失败:9999");
    }

    @Test
    void rejectedKeepsOrderWhenTransitionAbsent() {
        when(payCenterClient.execute(any(), eq(message))).thenReturn(answered("9999", null));

        F2fPayCenterFlow.Submitted submitted = flow.submit(preOrderSpec(null));

        assertInstanceOf(F2fPayCenterFlow.Submitted.Rejected.class, submitted);
        verify(orderMapper, never()).updateStatus(anyString(), any(), anyString(), any());
    }
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


    @Test
    void payResultNotifySkipsNonAppChannel() {
        givenOrderWithChannel(F2fChannel.TVM);
        flow.enqueuePayResultNotify(ORDER_NO, "TRADE1", LocalDateTime.now());
        verify(notifyService, never()).enqueue(anyString(), anyString(), any(), any());
        verify(notifyService, never()).enqueue(anyString(), anyString(), any(), any(), anyInt());

        givenOrderWithChannel(F2fChannel.BOM);
        flow.enqueuePayResultNotify(ORDER_NO, "TRADE1", LocalDateTime.now());
        verify(notifyService, never()).enqueue(anyString(), anyString(), any(), any());
        verify(notifyService, never()).enqueue(anyString(), anyString(), any(), any(), anyInt());
    }

    @Test
    void payResultNotifyEnqueuesAppChannel() {
        when(notifyService.enqueue(eq(F2fNotifyService.TYPE_PAY_RESULT), eq(ORDER_NO), any(), any()))
                .thenReturn(true);
        givenOrderWithChannel(F2fChannel.APP);

        flow.enqueuePayResultNotify(ORDER_NO, "TRADE1", LocalDateTime.now());

        verify(notifyService).enqueue(eq(F2fNotifyService.TYPE_PAY_RESULT), eq(ORDER_NO), any(), any());
    }

    @Test
    void payResultNotifyFallsBackToOneRetryWhenAppOrderMissesUserId() {
        F2fOrder appOrderWithoutUserId = new F2fOrder();
        appOrderWithoutUserId.setOrderNo(ORDER_NO);
        appOrderWithoutUserId.setChannel(F2fChannel.APP);
        when(orderMapper.selectByOrderNo(ORDER_NO)).thenReturn(appOrderWithoutUserId);

        flow.enqueuePayResultNotify(ORDER_NO, "TRADE1", LocalDateTime.now());

        verify(notifyService).enqueue(eq(F2fNotifyService.TYPE_PAY_RESULT), eq(ORDER_NO), any(), any(), eq(1));
    }

    private void givenOrderWithChannel(String channel) {
        F2fOrder order = new F2fOrder();
        order.setOrderNo(ORDER_NO);
        order.setChannel(channel);
        order.setThirdUserId("00522976");
        when(orderMapper.selectByOrderNo(ORDER_NO)).thenReturn(order);
    }

    private F2fPayCenterFlow.SubmitSpec preOrderSpec(F2fPayCenterFlow.RejectTransition transition) {
        return new F2fPayCenterFlow.SubmitSpec(ORDER_NO, 1, message, "二维码串", transition, false, "拉码下单");
    }

    private F2fPayCenterFlow.RejectTransition rejectToPayFailed() {
        return new F2fPayCenterFlow.RejectTransition(PENDING, "支付中心预下单失败:");
    }

    private PayCenterResult answered(String code, String data) {
        return PayCenterResults.answered(code, null, data);
    }

    private PayCenterResult answeredWith(PayCenterStatus status) {
        return PayCenterResults.answered("0", status, null);
    }

    private PayCenterResult transportFailed(String reason) {
        return PayCenterResults.transportFailed(reason);
    }
}
