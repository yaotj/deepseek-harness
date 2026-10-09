package com.chinasofti.huateng.dailyticket.service.payment;

import com.chinasofti.huateng.dailyticket.client.DailyTicketPayGatewayClient;
import com.chinasofti.huateng.dailyticket.config.DailyTicketPayProperties;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketInstanceMapper;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketOrderMapper;
import com.chinasofti.huateng.dailyticket.mapper.TravelTicketOrderMapper;
import com.chinasofti.huateng.dailyticket.model.DailyTicketOrder;
import com.chinasofti.huateng.dailyticket.model.TravelTicketOrder;
import com.chinasofti.huateng.dailyticket.service.DailyTicketPayResultNotifyService;
import com.chinasofti.huateng.dailyticket.service.paylog.DailyTicketPayLogWriter;
import com.chinasofti.huateng.dailyticket.service.refund.CanceledOrderRefundService;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketBaseResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketPayCallbackReqDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 已取消订单收到支付成功通知时的处置。
 *
 * <p>钉住三条：只走 {@code updatePayResultIfCanceled}（不碰要求 PAYING 的那条 CAS）、必须触发自动退款、
 * 支付失败通知不触发退款。
 */
class DailyTicketPaymentCanceledCallbackTest {

    private DailyTicketOrderMapper orderMapper;
    private TravelTicketOrderMapper travelOrderMapper;
    private CanceledOrderRefundService canceledOrderRefundService;
    private DailyTicketPaymentService service;

    @BeforeEach
    void setUp() {
        orderMapper = Mockito.mock(DailyTicketOrderMapper.class);
        travelOrderMapper = Mockito.mock(TravelTicketOrderMapper.class);
        canceledOrderRefundService = Mockito.mock(CanceledOrderRefundService.class);
        service = new DailyTicketPaymentService(
                Mockito.mock(DailyTicketPayGatewayClient.class),
                Mockito.mock(DailyTicketPayProperties.class),
                orderMapper,
                travelOrderMapper,
                Mockito.mock(DailyTicketInstanceMapper.class),
                Mockito.mock(DailyTicketPayLogWriter.class),
                Mockito.mock(DailyTicketPayResultNotifyService.class),
                canceledOrderRefundService);
    }

    @Test
    void canceledDailyOrderPaySuccessGoesToAutoRefund() {
        DailyTicketOrder order = new DailyTicketOrder();
        order.setOrderNo("0E1");
        order.setOrderStatus("CANCELED");
        order.setPayStatus("INIT");
        when(orderMapper.selectByOrderNo("0E1")).thenReturn(order);
        when(orderMapper.updatePayResultIfCanceled(any(DailyTicketOrder.class))).thenReturn(1);

        DailyTicketBaseResult result = service.receivePayResult(callback("0E1", "success"));

        assertEquals("0000", result.getRetCode());
        verify(orderMapper).updatePayResultIfCanceled(any(DailyTicketOrder.class));
        verify(orderMapper, never()).updatePayResultIfPaying(any(DailyTicketOrder.class));
        verify(canceledOrderRefundService).refundCanceledDailyOrder("0E1");
    }

    @Test
    void canceledDailyOrderPayFailedDoesNotRefund() {
        DailyTicketOrder order = new DailyTicketOrder();
        order.setOrderNo("0E2");
        order.setOrderStatus("CANCELED");
        order.setPayStatus("INIT");
        when(orderMapper.selectByOrderNo("0E2")).thenReturn(order);

        service.receivePayResult(callback("0E2", "failed"));

        verify(canceledOrderRefundService, never()).refundCanceledDailyOrder(anyString());
        verify(orderMapper, never()).updatePayResultIfCanceled(any(DailyTicketOrder.class));
    }

    @Test
    void canceledTravelOrderPaySuccessGoesToAutoRefund() {
        TravelTicketOrder parent = new TravelTicketOrder();
        parent.setOrderNo("0T1");
        parent.setOrderStatus("CANCELED");
        parent.setPayStatus("INIT");
        when(orderMapper.selectByOrderNo("0T1")).thenReturn(null);
        when(travelOrderMapper.selectByOrderNo("0T1")).thenReturn(parent);
        when(travelOrderMapper.updatePayResultIfCanceled(any(TravelTicketOrder.class))).thenReturn(1);

        DailyTicketBaseResult result = service.receivePayResult(callback("0T1", "SUCCESS"));

        assertEquals("0000", result.getRetCode());
        verify(travelOrderMapper).updatePayResultIfCanceled(any(TravelTicketOrder.class));
        verify(travelOrderMapper, never()).updatePayResultIfPaying(any(TravelTicketOrder.class));
        verify(canceledOrderRefundService).refundCanceledTravelOrder("0T1");
    }

    @Test
    void payingDailyOrderStillUsesPayingCas() {
        DailyTicketOrder order = new DailyTicketOrder();
        order.setOrderNo("0E3");
        order.setOrderStatus("PAYING");
        order.setPayStatus("PAYING");
        order.setTicketPrice(500);
        when(orderMapper.selectByOrderNo("0E3")).thenReturn(order);
        when(orderMapper.updatePayResultIfPaying(any(DailyTicketOrder.class))).thenReturn(1);

        service.receivePayResult(callback("0E3", "success"));

        verify(orderMapper).updatePayResultIfPaying(any(DailyTicketOrder.class));
        verify(orderMapper, never()).updatePayResultIfCanceled(any(DailyTicketOrder.class));
        verify(canceledOrderRefundService, never()).refundCanceledDailyOrder(anyString());
    }

    private DailyTicketPayCallbackReqDTO callback(String orderNo, String payResult) {
        DailyTicketPayCallbackReqDTO request = new DailyTicketPayCallbackReqDTO();
        request.setOrderNo(orderNo);
        request.setPayResult(payResult);
        request.setTradeNo("T-" + orderNo);
        request.setPaymentOrderNo("P-" + orderNo);
        request.setPayAmount(500);
        request.setPayDate(new Date());
        return request;
    }
}
