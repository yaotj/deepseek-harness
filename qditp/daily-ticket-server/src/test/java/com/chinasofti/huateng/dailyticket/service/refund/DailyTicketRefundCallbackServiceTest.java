package com.chinasofti.huateng.dailyticket.service.refund;

import com.chinasofti.huateng.dailyticket.mapper.DailyTicketOrderMapper;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketRefundMapper;
import com.chinasofti.huateng.dailyticket.mapper.TravelTicketOrderMapper;
import com.chinasofti.huateng.dailyticket.model.DailyTicketOrder;
import com.chinasofti.huateng.dailyticket.model.DailyTicketRefund;
import com.chinasofti.huateng.dailyticket.model.TravelTicketOrder;
import com.chinasofti.huateng.dailyticket.service.DailyTicketRefundNotifyService;
import com.chinasofti.huateng.dailyticket.service.paylog.DailyTicketPayLogWriter;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketBaseResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketRefundCallbackReqDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 钉住退款回调的白名单与幂等分支。
 *
 * <p>这些分支写错的后果都不会报错、只会静默错账：放宽白名单会让未受理的单被收口，
 * 重复到达返失败会让支付中心无限重推，「已 REFUNDED 收到失败」改状态会把已退的钱记成没退。
 */
class DailyTicketRefundCallbackServiceTest {

    private DailyTicketOrderMapper orderMapper;
    private TravelTicketOrderMapper travelOrderMapper;
    private DailyTicketRefundMapper refundMapper;
    private DailyTicketPayLogWriter payLogWriter;
    private DailyTicketRefundSettlementService settlementService;
    private DailyTicketRefundNotifyService refundNotifyService;
    private DailyTicketRefundCallbackService service;

    @BeforeEach
    void setUp() {
        orderMapper = mock(DailyTicketOrderMapper.class);
        travelOrderMapper = mock(TravelTicketOrderMapper.class);
        refundMapper = mock(DailyTicketRefundMapper.class);
        payLogWriter = mock(DailyTicketPayLogWriter.class);
        settlementService = mock(DailyTicketRefundSettlementService.class);
        refundNotifyService = mock(DailyTicketRefundNotifyService.class);
        service = new DailyTicketRefundCallbackService(orderMapper, travelOrderMapper, refundMapper,
                payLogWriter, settlementService, refundNotifyService);
    }

    private DailyTicketRefundCallbackReqDTO callback(String refundResult) {
        DailyTicketRefundCallbackReqDTO request = new DailyTicketRefundCallbackReqDTO();
        request.setOutRefundNo("R-0E01");
        request.setMerchantOrderNo("0E01");
        request.setRefundResult(refundResult);
        request.setRefundNo("PLAT-1");
        request.setRefundDate("20260921104500");
        return request;
    }

    /** 造一笔「日票 + 指定退款单状态 + 支付订单号匹配」的现场。 */
    private DailyTicketRefund givenDailyRefund(String refundStatus) {
        DailyTicketRefund refund = new DailyTicketRefund();
        refund.setOrderNo("0E01");
        refund.setRefundOrderNo("R-0E01");
        refund.setRefundStatus(refundStatus);
        refund.setOrderType("1");
        when(refundMapper.selectByRefundOrderNo("R-0E01")).thenReturn(refund);

        DailyTicketOrder order = new DailyTicketOrder();
        order.setOrderNo("0E01");
        order.setPayChannelCode("ALIPAY");
        when(orderMapper.selectByOrderNo("0E01")).thenReturn(order);
        return refund;
    }

    @Test
    void nullRequestIsRejected() {
        DailyTicketBaseResult result = service.receiveRefundResult(null);
        assertEquals("9999", result.getRetCode());
        assertEquals("退款回调报文为空", result.getRetMsg());
    }

    @Test
    void missingBothKeysIsRejected() {
        DailyTicketRefundCallbackReqDTO request = new DailyTicketRefundCallbackReqDTO();
        request.setRefundResult("SUCCESS");

        DailyTicketBaseResult result = service.receiveRefundResult(request);

        assertEquals("9999", result.getRetCode());
        assertEquals("outRefundNo与merchantOrderNo不能同时为空", result.getRetMsg());
        verify(payLogWriter, never()).insert(any(), any(), any(), any(), any());
    }

    @Test
    void unknownRefundIsRejectedButStillLogged() {
        DailyTicketBaseResult result = service.receiveRefundResult(callback("SUCCESS"));

        assertEquals("9999", result.getRetCode());
        assertEquals("退款记录不存在", result.getRetMsg());
        verify(payLogWriter).insert(eq("0E01"), eq("REFUND_CALLBACK"), any(), any(), any());
    }

    @Test
    void payOrderNoMismatchNeverTouchesBusinessRows() {
        DailyTicketRefund refund = givenDailyRefund("REFUNDING");
        DailyTicketOrder order = new DailyTicketOrder();
        order.setOrderNo("0E01");
        order.setPaymentOrderNo("PAY-A");
        when(orderMapper.selectByOrderNo("0E01")).thenReturn(order);

        DailyTicketRefundCallbackReqDTO request = callback("SUCCESS");
        request.setOrderNo("PAY-B");

        DailyTicketBaseResult result = service.receiveRefundResult(request);

        assertEquals("0000", result.getRetCode());
        verify(settlementService, never()).markRefunded(any(), any(), any());
        verify(settlementService, never()).markRefundFailed(any(), any(), any());
        assertEquals("REFUNDING", refund.getRefundStatus());
    }

    @Test
    void successOnRefundingSettles() {
        givenDailyRefund("REFUNDING");

        DailyTicketBaseResult result = service.receiveRefundResult(callback("SUCCESS"));

        assertEquals("0000", result.getRetCode());
        verify(settlementService).markRefunded(any(), any(), any());
        verify(refundNotifyService).deliverOne("0E01");
    }

    @Test
    void successOnWaitVerifySettles() {
        givenDailyRefund("WAIT_VERIFY");

        service.receiveRefundResult(callback("SUCCESS"));

        verify(settlementService).markRefunded(any(), any(), any());
    }

    @Test
    void successOnFailedCorrectsToRefunded() {
        givenDailyRefund("FAILED");

        service.receiveRefundResult(callback("SUCCESS"));

        verify(settlementService).markRefunded(any(), any(), any());
    }

    @Test
    void successOnAlreadyRefundedIsIdempotentAndRepushesNotify() {
        givenDailyRefund("REFUNDED");

        DailyTicketBaseResult result = service.receiveRefundResult(callback("SUCCESS"));

        assertEquals("0000", result.getRetCode());
        verify(settlementService).persistPlatformRefundNoIfChanged(any(), any());
        verify(settlementService, never()).markRefunded(any(), any(), any());
        verify(refundNotifyService).deliverOne("0E01");
    }

    @Test
    void successOnStatusOutsideWhitelistIsRejected() {
        givenDailyRefund("PENDING_AUDIT");

        DailyTicketBaseResult result = service.receiveRefundResult(callback("SUCCESS"));

        assertEquals("9999", result.getRetCode());
        assertEquals("退款单状态不允许收口: PENDING_AUDIT", result.getRetMsg());
        verify(settlementService, never()).markRefunded(any(), any(), any());
    }

    @Test
    void failureOnRefundingMarksFailed() {
        givenDailyRefund("REFUNDING");

        service.receiveRefundResult(callback("FAIL"));

        verify(settlementService).markRefundFailed(any(), any(), any());
        verify(refundNotifyService).deliverOne("0E01");
    }

    @Test
    void failureOnAlreadyRefundedKeepsRefunded() {
        DailyTicketRefund refund = givenDailyRefund("REFUNDED");

        DailyTicketBaseResult result = service.receiveRefundResult(callback("FAILED"));

        assertEquals("0000", result.getRetCode());
        assertEquals("REFUNDED", refund.getRefundStatus());
        verify(settlementService, never()).markRefundFailed(any(), any(), any());
    }

    @Test
    void failureOnAlreadyFailedIsIdempotent() {
        givenDailyRefund("FAILED");

        DailyTicketBaseResult result = service.receiveRefundResult(callback("FAILED"));

        assertEquals("0000", result.getRetCode());
        verify(settlementService, never()).markRefundFailed(any(), any(), any());
    }

    @Test
    void processingOnlyBackfillsPlatformRefundNo() {
        givenDailyRefund("REFUNDING");

        DailyTicketBaseResult result = service.receiveRefundResult(callback("PROCESSING"));

        assertEquals("0000", result.getRetCode());
        verify(settlementService).persistPlatformRefundNoIfChanged(any(), any());
        verify(settlementService, never()).markRefunded(any(), any(), any());
        verify(refundNotifyService, never()).deliverOne(anyString());
    }

    @Test
    void unknownResultNeverAdvancesState() {
        givenDailyRefund("REFUNDING");

        DailyTicketBaseResult result = service.receiveRefundResult(callback("WHATEVER"));

        assertEquals("0000", result.getRetCode());
        verify(settlementService, never()).markRefunded(any(), any(), any());
        verify(settlementService, never()).markRefundFailed(any(), any(), any());
        verify(refundNotifyService, never()).deliverOne(anyString());
    }

    @Test
    void travelRefundGoesToTravelSettlement() {
        DailyTicketRefund refund = new DailyTicketRefund();
        refund.setOrderNo("0T01");
        refund.setRefundOrderNo("R-0T01");
        refund.setRefundStatus("REFUNDING");
        refund.setOrderType("2");
        when(refundMapper.selectByRefundOrderNo("R-0T01")).thenReturn(refund);
        TravelTicketOrder parent = new TravelTicketOrder();
        parent.setOrderNo("0T01");
        parent.setPayChannelCode("ALIPAY");
        when(travelOrderMapper.selectByOrderNo("0T01")).thenReturn(parent);

        DailyTicketRefundCallbackReqDTO request = callback("SUCCESS");
        request.setOutRefundNo("R-0T01");
        request.setMerchantOrderNo("0T01");

        service.receiveRefundResult(request);

        verify(settlementService).markTravelRefunded(any(), any(), any());
        verify(settlementService, never()).markRefunded(any(), any(), any());
    }

    @Test
    void amountMismatchOnlyWarnsAndStillSettles() {
        DailyTicketRefund refund = givenDailyRefund("REFUNDING");
        refund.setRefundAmount(100);
        DailyTicketRefundCallbackReqDTO request = callback("SUCCESS");
        request.setRefundAmount("200");

        DailyTicketBaseResult result = service.receiveRefundResult(request);

        assertEquals("0000", result.getRetCode());
        verify(settlementService).markRefunded(any(), any(), any());
    }
}
