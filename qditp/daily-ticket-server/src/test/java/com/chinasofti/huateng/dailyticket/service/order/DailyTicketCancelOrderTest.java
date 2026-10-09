package com.chinasofti.huateng.dailyticket.service.order;

import com.chinasofti.huateng.dailyticket.mapper.DailyTicketInstanceMapper;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketOrderMapper;
import com.chinasofti.huateng.dailyticket.mapper.TravelTicketOrderMapper;
import com.chinasofti.huateng.dailyticket.model.DailyTicketInstance;
import com.chinasofti.huateng.dailyticket.model.DailyTicketOrder;
import com.chinasofti.huateng.dailyticket.model.TravelTicketOrder;
import com.chinasofti.huateng.dailyticket.service.payment.DailyTicketPaymentService;
import com.chinasofti.huateng.dailyticket.service.refund.CanceledOrderRefundService;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketBaseResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketOrderNoReqDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * IF8A-65 取消订单的状态白名单与自动退款触发。
 *
 * <p>钉住四条不可回退的口径：未激活即可取消（含已支付）、已激活一律拒、已支付单取消后必须触发退款、
 * 旅游票主单取消必须连带取消子单。
 */
class DailyTicketCancelOrderTest {

    private DailyTicketOrderMapper orderMapper;
    private TravelTicketOrderMapper travelOrderMapper;
    private DailyTicketInstanceMapper instanceMapper;
    private DailyTicketPaymentService paymentService;
    private CanceledOrderRefundService canceledOrderRefundService;
    private DailyTicketOrderCreationService service;

    @BeforeEach
    void setUp() {
        orderMapper = Mockito.mock(DailyTicketOrderMapper.class);
        travelOrderMapper = Mockito.mock(TravelTicketOrderMapper.class);
        instanceMapper = Mockito.mock(DailyTicketInstanceMapper.class);
        paymentService = Mockito.mock(DailyTicketPaymentService.class);
        canceledOrderRefundService = Mockito.mock(CanceledOrderRefundService.class);
        service = new DailyTicketOrderCreationService(orderMapper, travelOrderMapper, instanceMapper,
                paymentService, canceledOrderRefundService);
    }

    @Test
    void cancelCreatedDailyOrderWithoutRefund() {
        when(orderMapper.selectByOrderNo("0E1")).thenReturn(order("0E1", "CREATED", "INIT"));
        when(orderMapper.cancelIfPending("0E1")).thenReturn(1);

        DailyTicketBaseResult result = service.cancelOrder(request("0E1", "1"));

        assertEquals("0000", result.getRetCode());
        verify(orderMapper).cancelIfPending("0E1");
        verify(canceledOrderRefundService, never()).refundCanceledDailyOrder(anyString());
    }

    @Test
    void cancelPaidDailyOrderTriggersAutoRefund() {
        when(orderMapper.selectByOrderNo("0E2")).thenReturn(order("0E2", "PAID", "PAID"));
        when(orderMapper.cancelIfPaid("0E2")).thenReturn(1);

        DailyTicketBaseResult result = service.cancelOrder(request("0E2", "1"));

        assertEquals("0000", result.getRetCode());
        verify(orderMapper).cancelIfPaid("0E2");
        verify(canceledOrderRefundService).refundCanceledDailyOrder("0E2");
        verify(orderMapper, never()).cancelIfPending(anyString());
    }

    @Test
    void rejectCancelWhenTicketActivated() {
        when(orderMapper.selectByOrderNo("0E3")).thenReturn(order("0E3", "PAID", "PAID"));
        when(instanceMapper.selectByOrderNo("0E3")).thenReturn(new DailyTicketInstance());

        DailyTicketBaseResult result = service.cancelOrder(request("0E3", "1"));

        assertEquals("9999", result.getRetCode());
        verify(orderMapper, never()).cancelIfPaid(anyString());
        verify(orderMapper, never()).cancelIfPending(anyString());
        verify(canceledOrderRefundService, never()).refundCanceledDailyOrder(anyString());
    }

    @Test
    void cancelAlreadyCanceledOrderIsIdempotent() {
        when(orderMapper.selectByOrderNo("0E4")).thenReturn(order("0E4", "CANCELED", "INIT"));

        DailyTicketBaseResult result = service.cancelOrder(request("0E4", "1"));

        assertEquals("0000", result.getRetCode());
        verify(orderMapper, never()).cancelIfPending(anyString());
        verify(orderMapper, never()).cancelIfPaid(anyString());
    }

    @Test
    void rejectCancelWhenOrderAlreadyRefunding() {
        when(orderMapper.selectByOrderNo("0E5")).thenReturn(order("0E5", "REFUNDING", "PAID"));
        when(orderMapper.cancelIfPending("0E5")).thenReturn(0);

        DailyTicketBaseResult result = service.cancelOrder(request("0E5", "1"));

        assertEquals("9999", result.getRetCode());
        verify(canceledOrderRefundService, never()).refundCanceledDailyOrder(anyString());
    }

    @Test
    void cancelPaidTravelOrderCancelsSubOrdersAndRefunds() {
        TravelTicketOrder parent = new TravelTicketOrder();
        parent.setOrderNo("0T1");
        parent.setOrderStatus("PAID");
        parent.setPayStatus("PAID");
        when(travelOrderMapper.selectByOrderNo("0T1")).thenReturn(parent);
        when(orderMapper.selectByParentOrderNo("0T1")).thenReturn(List.of(order("0E6", "CREATED", "INIT")));
        when(travelOrderMapper.cancelIfPaid("0T1")).thenReturn(1);

        DailyTicketBaseResult result = service.cancelOrder(request("0T1", "2"));

        assertEquals("0000", result.getRetCode());
        verify(travelOrderMapper).cancelIfPaid("0T1");
        verify(orderMapper).cancelSubOrdersByParent("0T1");
        verify(canceledOrderRefundService).refundCanceledTravelOrder("0T1");
    }

    @Test
    void rejectCancelTravelOrderWhenAnySubTicketActivated() {
        TravelTicketOrder parent = new TravelTicketOrder();
        parent.setOrderNo("0T2");
        parent.setOrderStatus("PAID");
        parent.setPayStatus("PAID");
        when(travelOrderMapper.selectByOrderNo("0T2")).thenReturn(parent);
        when(orderMapper.selectByParentOrderNo("0T2")).thenReturn(List.of(order("0E7", "CREATED", "INIT")));
        when(instanceMapper.selectByOrderNo("0E7")).thenReturn(new DailyTicketInstance());

        DailyTicketBaseResult result = service.cancelOrder(request("0T2", "2"));

        assertEquals("9999", result.getRetCode());
        verify(travelOrderMapper, never()).cancelIfPaid(anyString());
        verify(orderMapper, never()).cancelSubOrdersByParent(anyString());
        verify(canceledOrderRefundService, never()).refundCanceledTravelOrder(anyString());
    }

    private DailyTicketOrderNoReqDTO request(String orderNo, String orderType) {
        DailyTicketOrderNoReqDTO request = new DailyTicketOrderNoReqDTO();
        request.setOrderNo(orderNo);
        request.setOrderType(orderType);
        return request;
    }

    private DailyTicketOrder order(String orderNo, String orderStatus, String payStatus) {
        DailyTicketOrder order = new DailyTicketOrder();
        order.setOrderNo(orderNo);
        order.setOrderStatus(orderStatus);
        order.setPayStatus(payStatus);
        order.setTicketPrice(500);
        return order;
    }
}
