package com.chinasofti.huateng.dailyticket.service.refund;

import com.chinasofti.huateng.dailyticket.mapper.DailyTicketOrderMapper;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketPayLogMapper;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketRefundDetailMapper;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketRefundMapper;
import com.chinasofti.huateng.dailyticket.mapper.TravelTicketOrderMapper;
import com.chinasofti.huateng.dailyticket.model.DailyTicketOrder;
import com.chinasofti.huateng.dailyticket.model.DailyTicketRefund;
import com.chinasofti.huateng.dailyticket.model.TravelTicketOrder;
import com.chinasofti.huateng.dailyticket.service.DailyTicketRefundNotifyService;
import com.chinasofti.huateng.dailyticket.service.travel.TravelParentSummaryWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 钉住退款收口的四条终态链路。
 *
 * <p>最重要的一条是**每个终态入口都必须调 {@code deliverOne}** —— 2026-09-20 实测漏掉它时
 * 四笔退款的 `NOTIFY_STATUS` 全停在 PENDING、APP 一次都没收到退款结果，而日志一片正常。
 */
class DailyTicketRefundSettlementServiceTest {

    private DailyTicketOrderMapper orderMapper;
    private TravelTicketOrderMapper travelOrderMapper;
    private DailyTicketRefundMapper refundMapper;
    private DailyTicketRefundDetailMapper refundDetailMapper;
    private DailyTicketPayLogMapper payLogMapper;
    private DailyTicketRefundNotifyService refundNotifyService;
    private DailyTicketTicketLockWriter ticketLockWriter;
    private TravelParentSummaryWriter travelParentSummaryWriter;
    private DailyTicketRefundSettlementService service;

    @BeforeEach
    void setUp() {
        orderMapper = mock(DailyTicketOrderMapper.class);
        travelOrderMapper = mock(TravelTicketOrderMapper.class);
        refundMapper = mock(DailyTicketRefundMapper.class);
        refundDetailMapper = mock(DailyTicketRefundDetailMapper.class);
        payLogMapper = mock(DailyTicketPayLogMapper.class);
        refundNotifyService = mock(DailyTicketRefundNotifyService.class);
        ticketLockWriter = mock(DailyTicketTicketLockWriter.class);
        travelParentSummaryWriter = mock(TravelParentSummaryWriter.class);
        service = new DailyTicketRefundSettlementService(orderMapper, travelOrderMapper, refundMapper,
                refundDetailMapper, payLogMapper, refundNotifyService, ticketLockWriter, travelParentSummaryWriter);
    }

    private DailyTicketRefund refund(String orderNo, String scope) {
        DailyTicketRefund refund = new DailyTicketRefund();
        refund.setOrderNo(orderNo);
        refund.setRefundOrderNo("R-" + orderNo);
        refund.setRefundScope(scope);
        return refund;
    }

    private DailyTicketOrder order(String orderNo) {
        DailyTicketOrder order = new DailyTicketOrder();
        order.setOrderNo(orderNo);
        return order;
    }

    private TravelTicketOrder parent(String orderNo) {
        TravelTicketOrder parent = new TravelTicketOrder();
        parent.setOrderNo(orderNo);
        return parent;
    }

    @Test
    void markRefundedSettlesOrderTicketAndPushesNotify() {
        DailyTicketRefund refund = refund("0E01", null);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("refundOrderNo", "PLAT-1");
        data.put("refundTime", "20260921103000");

        service.markRefunded(order("0E01"), refund, data);

        assertEquals("REFUNDED", refund.getRefundStatus());
        assertEquals("PLAT-1", refund.getPlatformRefundNo());
        assertNotNull(refund.getRefundDate());
        verify(orderMapper).updateOrderStatus("0E01", "REFUNDED");
        verify(ticketLockWriter).settleOnRefunded("0E01");
        verify(refundMapper).updateNotifyStatus(eq("0E01"), eq("PENDING"), eq(0), isNull(), isNull());
        verify(refundNotifyService).deliverOne("0E01");
    }

    @Test
    void markRefundFailedRollsOrderBackAndReleasesTicket() {
        DailyTicketRefund refund = refund("0E02", null);

        service.markRefundFailed(order("0E02"), refund, null);

        assertEquals("FAILED", refund.getRefundStatus());
        assertNull(refund.getRefundDate());
        verify(orderMapper).updateOrderStatus("0E02", "PAID");
        verify(ticketLockWriter).releaseLock("0E02");
        verify(refundNotifyService).deliverOne("0E02");
    }

    @Test
    void markRefundingDoesNotPushNotify() {
        DailyTicketRefund refund = refund("0E03", null);

        service.markRefunding(order("0E03"), refund);

        assertEquals("REFUNDING", refund.getRefundStatus());
        verify(orderMapper).updateOrderStatus("0E03", "REFUNDING");
        verify(refundNotifyService, never()).deliverOne(any());
    }

    @Test
    void travelFullRefundClosesParentDirectly() {
        DailyTicketRefund refund = refund("0T01", "TRAVEL_FULL");
        when(orderMapper.selectByParentOrderNo("0T01")).thenReturn(Arrays.asList(order("C1"), order("C2")));

        service.markTravelRefunded(parent("0T01"), refund, null);

        verify(refundDetailMapper).updateStatusByRefundOrderNo("R-0T01", "REFUNDED");
        verify(ticketLockWriter).settleOnRefunded("C1");
        verify(ticketLockWriter).settleOnRefunded("C2");
        verify(travelOrderMapper).updateOrderStatus("0T01", "REFUNDED");
        verify(travelParentSummaryWriter, never()).refresh(any());
        verify(refundNotifyService).deliverOne("0T01");
    }

    @Test
    void travelSubRefundOnlySettlesThatChildAndRecomputesParent() {
        DailyTicketRefund refund = refund("C1", "TRAVEL_SUB");
        when(orderMapper.selectByOrderNo("C1")).thenReturn(order("C1"));

        service.markTravelRefunded(parent("0T02"), refund, null);

        verify(ticketLockWriter).settleOnRefunded("C1");
        verify(travelOrderMapper, never()).updateOrderStatus(eq("0T02"), eq("REFUNDED"));
        verify(travelParentSummaryWriter).refresh("0T02");
        verify(refundNotifyService).deliverOne("C1");
    }

    @Test
    void travelSubRefundFailureUnlocksChildAndRecomputesParent() {
        DailyTicketRefund refund = refund("C1", "TRAVEL_SUB");

        service.markTravelRefundFailed(parent("0T03"), refund, null);

        assertEquals("FAILED", refund.getRefundStatus());
        verify(refundDetailMapper).updateStatusByRefundOrderNo("R-C1", "FAILED");
        verify(ticketLockWriter).releaseLock("C1");
        verify(travelParentSummaryWriter).refresh("0T03");
        verify(refundNotifyService).deliverOne("C1");
    }

    @Test
    void travelFullRefundFailureUnlocksAllChildrenAndRestoresPaid() {
        DailyTicketRefund refund = refund("0T04", "TRAVEL_FULL");
        when(orderMapper.selectByParentOrderNo("0T04")).thenReturn(Arrays.asList(order("C1"), order("C2")));

        service.markTravelRefundFailed(parent("0T04"), refund, null);

        verify(ticketLockWriter).releaseLock("C1");
        verify(ticketLockWriter).releaseLock("C2");
        verify(travelOrderMapper).updateOrderStatus("0T04", "PAID");
        verify(refundNotifyService).deliverOne("0T04");
    }

    @Test
    void platformRefundNoPrefersDocumentedFieldThenFallsBack() {
        DailyTicketRefund refund = refund("0E04", null);
        Map<String, Object> both = new LinkedHashMap<>();
        both.put("refundOrderNo", "DOC");
        both.put("refundNo", "LEGACY");
        service.updatePlatformRefundNo(refund, both);
        assertEquals("DOC", refund.getPlatformRefundNo());

        DailyTicketRefund legacyOnly = refund("0E05", null);
        Map<String, Object> legacy = new LinkedHashMap<>();
        legacy.put("refundNo", "LEGACY");
        service.updatePlatformRefundNo(legacyOnly, legacy);
        assertEquals("LEGACY", legacyOnly.getPlatformRefundNo());

        DailyTicketRefund untouched = refund("0E06", null);
        untouched.setPlatformRefundNo("KEEP");
        service.updatePlatformRefundNo(untouched, new LinkedHashMap<>());
        assertEquals("KEEP", untouched.getPlatformRefundNo());
    }

    @Test
    void persistPlatformRefundNoSkipsWriteWhenUnchanged() {
        DailyTicketRefund refund = refund("0E07", null);
        refund.setPlatformRefundNo("SAME");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("refundOrderNo", "SAME");

        service.persistPlatformRefundNoIfChanged(refund, data);

        verify(refundMapper, never()).updateResult(any());
    }

    @Test
    void gatewayRefundDateFallsBackOnBadFormat() {
        Date fallback = new Date(0L);
        assertSame(fallback, service.parseGatewayRefundDate("not-a-date", fallback));
        assertSame(fallback, service.parseGatewayRefundDate("  ", fallback));
        assertNotNull(service.parseGatewayRefundDate("20260921103000", fallback));
    }
}
