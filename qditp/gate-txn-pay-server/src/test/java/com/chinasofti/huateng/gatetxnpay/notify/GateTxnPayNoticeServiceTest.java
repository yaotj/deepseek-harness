package com.chinasofti.huateng.gatetxnpay.notify;

import com.chinasofti.huateng.gatetxnpay.entity.GateTxnPay;
import com.chinasofti.huateng.gatetxnpay.mapper.GateTxnPayMapper;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 行程订单通知的触发边界与重试语义：
 * 只有终态才推；过程态一条都不能推；首推失败必须按退避重投而不是丢掉。
 */
class GateTxnPayNoticeServiceTest {

    private final OrderPayNoticeNotifier notifier = mock(OrderPayNoticeNotifier.class);
    private final GateTxnPayMapper mapper = mock(GateTxnPayMapper.class);
    private final ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
    private final Executor directExecutor = Runnable::run;

    @Test
    void successIsNotified() {
        GateTxnPay order = order();
        when(notifier.push(order, "SUCCESS")).thenReturn(true);

        service().notifySettled(order, "SUCCESS", "支付结果回调收敛");

        verify(notifier).push(order, "SUCCESS");
    }

    @Test
    void failIsAlsoNotified() {
        GateTxnPay order = order();
        when(notifier.push(order, "FAIL")).thenReturn(true);

        service().notifySettled(order, "FAIL", "扣费被pay-sign业务拒绝");

        verify(notifier).push(order, "FAIL");
    }

    @Test
    void processStatesAreNeverNotified() {
        GateTxnPayNoticeService service = service();

        service.notifySettled(order(), "PROCESSING", "扣费已被pay-sign受理");
        service.notifySettled(order(), "RETRY", "扣费未获pay-sign答复");
        service.notifySettled(order(), "INIT", "建单");

        verify(notifier, never()).push(any(), any());
    }

    @Test
    void nullOrderIsSkipped() {
        service().notifySettled(null, "SUCCESS", "支付结果回调收敛");

        verify(notifier, never()).push(any(), any());
    }

    @Test
    void notifierFailureNeverEscapesToCaller() {
        GateTxnPay order = order();
        doThrow(new IllegalStateException("gateway down")).when(notifier).push(order, "SUCCESS");

        service().notifySettled(order, "SUCCESS", "支付结果回调收敛");

        verify(notifier).push(eq(order), eq("SUCCESS"));
    }

    @Test
    void firstPushRejectedIsRetriedBySchedule() {
        GateTxnPay order = order();
        when(notifier.push(order, "SUCCESS")).thenReturn(false);
        runScheduledTasksImmediately();

        service().notifySettled(order, "SUCCESS", "支付结果回调收敛");

        verify(notifier, times(4)).push(order, "SUCCESS");
        verify(scheduler, times(3)).schedule(any(Runnable.class), anyLong(), any());
    }

    @Test
    void retryStopsOnceAccepted() {
        GateTxnPay order = order();
        when(notifier.push(order, "SUCCESS")).thenReturn(false, true);
        runScheduledTasksImmediately();

        service().notifySettled(order, "SUCCESS", "支付结果回调收敛");

        verify(notifier, times(2)).push(order, "SUCCESS");
        verify(scheduler, times(1)).schedule(any(Runnable.class), anyLong(), any());
    }

    @Test
    void repushRejectsUnknownOrder() {
        when(mapper.selectByOrderNo("GT404")).thenReturn(null);

        assertEquals("未找到过闸扣费订单：GT404", service().repush("GT404"));
        verify(notifier, never()).push(any(), any());
    }

    @Test
    void repushRejectsProcessState() {
        GateTxnPay order = order();
        order.setDebitStatus("PROCESSING");
        when(mapper.selectByOrderNo(order.getOrderNo())).thenReturn(order);

        assertEquals("订单非终态，不推送：PROCESSING", service().repush(order.getOrderNo()));
        verify(notifier, never()).push(any(), any());
    }

    @Test
    void repushPushesSettledOrderSynchronously() {
        GateTxnPay order = order();
        order.setDebitStatus("FAIL");
        when(mapper.selectByOrderNo(order.getOrderNo())).thenReturn(order);
        when(notifier.push(order, "FAIL")).thenReturn(true);

        assertNull(service().repush(order.getOrderNo()), "受理成功返回 null");
        verify(notifier).push(order, "FAIL");
    }

    private void runScheduledTasksImmediately() {
        when(scheduler.schedule(any(Runnable.class), anyLong(), any())).thenAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        });
    }

    private GateTxnPayNoticeService service() {
        return new GateTxnPayNoticeService(directExecutor, scheduler, notifier, mapper, "5000,15000,30000");
    }

    private GateTxnPay order() {
        GateTxnPay order = new GateTxnPay();
        order.setOrderNo("GT20261120110545409000081");
        return order;
    }
}
