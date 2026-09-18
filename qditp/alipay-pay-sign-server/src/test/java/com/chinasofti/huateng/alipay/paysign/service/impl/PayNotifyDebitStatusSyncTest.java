package com.chinasofti.huateng.alipay.paysign.service.impl;

import com.chinasofti.huateng.alipay.paysign.service.impl.payment.PaymentQueryService;

import com.chinasofti.huateng.alipay.paysign.entity.AlipayPayCallbackLog;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipayPayCallbackLogMapper;
import com.chinasofti.huateng.alipay.paysign.port.DebitSyncPort;
import com.chinasofti.huateng.common.response.AlipayCommonResponse;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripPayNotifyReqDTO;
import com.chinasofti.huateng.rpc.outcome.RpcOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 钉住支付宝出行扣费回调的收敛口。
 *
 * <p>批次 3（ADR-D131）起，出网点是 {@link DebitSyncPort}，retCode 判读已挪进 adapter，
 * 本类只钉住「三态怎么折回 boolean」：{@code Ok} 放行、{@code BizRejected} 与 {@code Unreachable}
 * 都让支付中心重推。**NEVER 在这里再断言 retCode 字面量**，那是 adapter 的职责。</p>
 *
 * <p>另钉住「先 INSERT 再计数」这一顺序：方法无事务，只有 INSERT 先自动提交，COUNT 才含本次、
 * 限次才按真实推送次数触发。**NEVER 把 {@code insert} 挪到 {@code countByOrderNo} 之后。**</p>
 */
class PayNotifyDebitStatusSyncTest {

    private static final String ORDER_NO = "GT20260914021300000000001";
    private static final String CALLBACK_TYPE_PAY = "PAY";

    private DebitSyncPort debitSyncPort;
    private AlipayPayCallbackLogMapper callbackLogMapper;
    private PaymentQueryService service;

    @BeforeEach
    void setUp() throws ReflectiveOperationException {
        debitSyncPort = mock(DebitSyncPort.class);
        callbackLogMapper = mock(AlipayPayCallbackLogMapper.class);
        service = new PaymentQueryService();
        inject("debitSyncPort", debitSyncPort);
        inject("alipayPayCallbackLogMapper", callbackLogMapper);
    }

    private void inject(String name, Object value) throws ReflectiveOperationException {
        Field field = PaymentQueryService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(service, value);
    }

    @Test
    void transStatusOneIsSyncedAsSuccess() {
        when(debitSyncPort.syncDebitStatus(anyString(), anyString(), any())).thenReturn(new RpcOutcome.Ok());

        AlipayCommonResponse response = service.handlePayNotify(notify("1"));

        assertEquals("0000", response.getRetCode(), "远端确认收敛后 MUST 对支付中心返成功");
        assertEquals("SUCCESS", capturedPayStatus(), "transStatus=1 MUST 同步成 SUCCESS");
    }

    @Test
    void nonOneTransStatusIsSyncedAsFail() {
        when(debitSyncPort.syncDebitStatus(anyString(), anyString(), any())).thenReturn(new RpcOutcome.Ok());

        service.handlePayNotify(notify("2"));

        assertEquals("FAIL", capturedPayStatus(), "非 1 的 transStatus MUST 同步成 FAIL");
    }

    @Test
    void bizRejectedMakesCallbackFailSoPayCenterRepushes() {
        when(debitSyncPort.syncDebitStatus(anyString(), anyString(), any()))
                .thenReturn(new RpcOutcome.BizRejected("9999", "扣费订单不存在"));

        AlipayCommonResponse response = service.handlePayNotify(notify("1"));

        assertEquals("9999", response.getRetCode(),
                "远端没收敛时 MUST 返非 0000，NEVER 因为「没抛异常」就报成功");
    }

    @Test
    void unreachableMakesCallbackFail() {
        when(debitSyncPort.syncDebitStatus(anyString(), anyString(), any()))
                .thenReturn(new RpcOutcome.Unreachable(new IllegalStateException("gate-txn-pay 响应为空")));

        AlipayCommonResponse response = service.handlePayNotify(notify("1"));

        assertEquals("9999", response.getRetCode(), "对端未获答复 MUST 返非 0000");
    }

    @Test
    void blankOrderNoIsRejectedBeforeAnyRpc() {
        AlipayCommonResponse response = service.handlePayNotify(notify("1", " "));

        assertEquals("8001", response.getRetCode(), "订单号为空 MUST 返参数异常");
        verify(debitSyncPort, never()).syncDebitStatus(anyString(), anyString(), any());
    }

    @Test
    void evidenceIsInsertedBeforeCounting() {
        when(debitSyncPort.syncDebitStatus(anyString(), anyString(), any())).thenReturn(new RpcOutcome.Ok());

        service.handlePayNotify(notify("1"));

        InOrder order = inOrder(callbackLogMapper);
        order.verify(callbackLogMapper).insert(any(AlipayPayCallbackLog.class));
        order.verify(callbackLogMapper).countByOrderNo(ORDER_NO, CALLBACK_TYPE_PAY);
    }

    @Test
    void countReachingLimitStopsRepushAndMarksManual() {
        when(callbackLogMapper.countByOrderNo(ORDER_NO, CALLBACK_TYPE_PAY)).thenReturn(2);
        when(debitSyncPort.syncDebitStatus(anyString(), anyString(), any()))
                .thenReturn(new RpcOutcome.Unreachable(new IllegalStateException("gate-txn-pay 不可达")));

        AlipayCommonResponse response = service.handlePayNotify(notify("1"));

        assertEquals("0000", response.getRetCode(), "计数达上限 MUST 返 0000 让支付中心停推");
        verify(callbackLogMapper).markManualByOrderNo(eq(ORDER_NO), eq(CALLBACK_TYPE_PAY), anyString());
        verify(callbackLogMapper, never()).updateHandleResult(anyString(), anyString(), any());
    }

    private String capturedPayStatus() {
        ArgumentCaptor<String> orderNo = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payStatus = ArgumentCaptor.forClass(String.class);
        verify(debitSyncPort).syncDebitStatus(orderNo.capture(), payStatus.capture(), any());
        assertEquals(ORDER_NO, orderNo.getValue());
        return payStatus.getValue();
    }

    private AlipayTripPayNotifyReqDTO notify(String transStatus) {
        return notify(transStatus, ORDER_NO);
    }

    private AlipayTripPayNotifyReqDTO notify(String transStatus, String orderNo) {
        AlipayTripPayNotifyReqDTO request = new AlipayTripPayNotifyReqDTO();
        request.setOrderNo(orderNo);
        request.setTransStatus(transStatus);
        request.setTransAmount("400");
        request.setChannelVoucherId("2026091422001");
        request.setTransTime("2026-09-14 02:13:00");
        return request;
    }
}
