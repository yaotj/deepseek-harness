package com.chinasofti.huateng.dailyticket.service.travel;

import com.chinasofti.huateng.dailyticket.mapper.DailyTicketInstanceMapper;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketOrderMapper;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketRefundMapper;
import com.chinasofti.huateng.dailyticket.mapper.TravelTicketOrderMapper;
import com.chinasofti.huateng.dailyticket.model.DailyTicketInstance;
import com.chinasofti.huateng.dailyticket.model.DailyTicketOrder;
import com.chinasofti.huateng.dailyticket.model.DailyTicketRefund;
import com.chinasofti.huateng.dailyticket.model.TravelTicketOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 钉住旅游票主单汇总的五个状态分支与三处短路。
 *
 * <p>判定优先级（退款语义优先于使用语义）写反不会报错，只会让对账取不到退款，因此逐条断言。
 */
class TravelParentSummaryWriterTest {

    private static final String PARENT = "0T-PARENT-1";

    private TravelTicketOrderMapper travelOrderMapper;
    private DailyTicketOrderMapper orderMapper;
    private DailyTicketInstanceMapper instanceMapper;
    private DailyTicketRefundMapper refundMapper;
    private TravelParentSummaryWriter writer;

    @BeforeEach
    void setUp() {
        travelOrderMapper = mock(TravelTicketOrderMapper.class);
        orderMapper = mock(DailyTicketOrderMapper.class);
        instanceMapper = mock(DailyTicketInstanceMapper.class);
        refundMapper = mock(DailyTicketRefundMapper.class);
        writer = new TravelParentSummaryWriter(travelOrderMapper, orderMapper, instanceMapper, refundMapper);
    }

    private void givenPaidParentWithChildren(String... childOrderNos) {
        TravelTicketOrder parent = new TravelTicketOrder();
        parent.setOrderNo(PARENT);
        parent.setPayStatus("PAID");
        when(travelOrderMapper.selectByOrderNo(PARENT)).thenReturn(parent);

        List<DailyTicketOrder> children = new ArrayList<>();
        for (String no : childOrderNos) {
            DailyTicketOrder child = new DailyTicketOrder();
            child.setOrderNo(no);
            child.setParentOrderNo(PARENT);
            children.add(child);
        }
        when(orderMapper.selectByParentOrderNo(PARENT)).thenReturn(children);
    }

    private void givenTicketStatus(String childOrderNo, String ticketStatus) {
        DailyTicketInstance ticket = new DailyTicketInstance();
        ticket.setTicketStatus(ticketStatus);
        when(instanceMapper.selectByOrderNo(childOrderNo)).thenReturn(ticket);
    }

    private void givenRefundStatus(String childOrderNo, String refundStatus) {
        DailyTicketRefund refund = new DailyTicketRefund();
        refund.setRefundStatus(refundStatus);
        when(refundMapper.selectByOrderNo(childOrderNo)).thenReturn(refund);
    }

    @Test
    void allChildrenRefundedMakesParentRefunded() {
        givenPaidParentWithChildren("C1", "C2");
        givenRefundStatus("C1", "REFUNDED");
        givenRefundStatus("C2", "REFUNDED");

        writer.refresh(PARENT);

        verify(travelOrderMapper).updateOrderStatus(PARENT, "REFUNDED");
    }

    @Test
    void someChildrenRefundedMakesParentPartialRefunded() {
        givenPaidParentWithChildren("C1", "C2");
        givenRefundStatus("C1", "REFUNDED");

        writer.refresh(PARENT);

        verify(travelOrderMapper).updateOrderStatus(PARENT, "PARTIAL_REFUNDED");
    }

    @Test
    void refundTakesPrecedenceOverUsed() {
        givenPaidParentWithChildren("C1", "C2");
        givenTicketStatus("C1", "USED");
        givenTicketStatus("C2", "USED");
        givenRefundStatus("C1", "REFUNDED");

        writer.refresh(PARENT);

        verify(travelOrderMapper).updateOrderStatus(PARENT, "PARTIAL_REFUNDED");
    }

    @Test
    void allChildrenUsedOrExpiredMakesParentUsed() {
        givenPaidParentWithChildren("C1", "C2");
        givenTicketStatus("C1", "USED");
        givenTicketStatus("C2", "EXPIRED");

        writer.refresh(PARENT);

        verify(travelOrderMapper).updateOrderStatus(PARENT, "USED");
    }

    @Test
    void someChildrenUsedMakesParentPartialUsed() {
        givenPaidParentWithChildren("C1", "C2");
        givenTicketStatus("C1", "USED");
        givenTicketStatus("C2", "ACTIVATED");

        writer.refresh(PARENT);

        verify(travelOrderMapper).updateOrderStatus(PARENT, "PARTIAL_USED");
    }

    @Test
    void untouchedChildrenKeepParentPaid() {
        givenPaidParentWithChildren("C1", "C2");
        givenTicketStatus("C1", "ACTIVATED");
        givenTicketStatus("C2", "ACTIVATED");

        writer.refresh(PARENT);

        verify(travelOrderMapper).updateOrderStatus(PARENT, "PAID");
    }

    @Test
    void unpaidParentIsNeverTouched() {
        TravelTicketOrder parent = new TravelTicketOrder();
        parent.setOrderNo(PARENT);
        parent.setPayStatus("UNPAID");
        when(travelOrderMapper.selectByOrderNo(PARENT)).thenReturn(parent);

        writer.refresh(PARENT);

        verify(travelOrderMapper, never()).updateOrderStatus(anyString(), anyString());
    }

    @Test
    void missingParentIsNeverTouched() {
        when(travelOrderMapper.selectByOrderNo(PARENT)).thenReturn(null);

        writer.refresh(PARENT);

        verify(travelOrderMapper, never()).updateOrderStatus(anyString(), anyString());
    }

    @Test
    void emptyChildrenIsNeverTouched() {
        TravelTicketOrder parent = new TravelTicketOrder();
        parent.setOrderNo(PARENT);
        parent.setPayStatus("PAID");
        when(travelOrderMapper.selectByOrderNo(PARENT)).thenReturn(parent);
        when(orderMapper.selectByParentOrderNo(PARENT)).thenReturn(Collections.emptyList());

        writer.refresh(PARENT);

        verify(travelOrderMapper, never()).updateOrderStatus(anyString(), anyString());
    }

    @Test
    void refreshBySubOrderResolvesParent() {
        DailyTicketOrder sub = new DailyTicketOrder();
        sub.setOrderNo("C1");
        sub.setParentOrderNo(PARENT);
        when(orderMapper.selectByOrderNo("C1")).thenReturn(sub);

        TravelTicketOrder parent = new TravelTicketOrder();
        parent.setOrderNo(PARENT);
        parent.setPayStatus("PAID");
        when(travelOrderMapper.selectByOrderNo(PARENT)).thenReturn(parent);
        when(orderMapper.selectByParentOrderNo(PARENT)).thenReturn(Arrays.asList(sub));

        writer.refreshBySubOrder("C1");

        verify(travelOrderMapper).updateOrderStatus(PARENT, "PAID");
    }

    @Test
    void refreshBySubOrderSkipsPlainDailyTicket() {
        DailyTicketOrder plain = new DailyTicketOrder();
        plain.setOrderNo("0E1");
        when(orderMapper.selectByOrderNo("0E1")).thenReturn(plain);

        writer.refreshBySubOrder("0E1");

        verify(travelOrderMapper, never()).updateOrderStatus(anyString(), anyString());
    }
}
