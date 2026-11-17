package com.chinasofti.huateng.transquery.query;

import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripPayTxnBriefDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripTravelDetailDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayListDTO;
import com.chinasofti.huateng.rpc.alipay.paysign.AlipayPaySignClient;
import com.chinasofti.huateng.rpc.pay.GateTxnPayClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


class AlipayTravelDetailFromGateTxnPayTest {

    private static final String THIRD_USER_ID = "2088";

    private GateTxnPayClient gateTxnPayClient;
    private AlipayPaySignClient alipayPaySignClient;
    private AlipayTravelQueryHandler handler;

    @BeforeEach
    void setUp() {
        gateTxnPayClient = mock(GateTxnPayClient.class);
        alipayPaySignClient = mock(AlipayPaySignClient.class);
        handler = new AlipayTravelQueryHandler(alipayPaySignClient, gateTxnPayClient);
    }

    @Test
    void journeyFieldsAllComeFromGateTxnPay() {
        when(gateTxnPayClient.queryByOrderNo("GT001")).thenReturn(order());

        AlipayTripFindTravelDetailRespDTO resp = handler.findTravelDetail(request("GT001"));

        assertEquals("0000", resp.getRetCode());
        AlipayTripTravelDetailDTO detail = resp.getData();
        assertNotNull(detail);
        assertEquals("五四广场", detail.getEntryStationName());
        assertEquals("20260914080000", detail.getEntryDate());
        assertEquals("台东", detail.getExitStationName());
        assertEquals("20260914083000", detail.getExitDate());
        assertEquals("200", detail.getPayAmount());
        assertEquals("500", detail.getTotalAmount());
        assertEquals("0", detail.getOrderExpType());
        assertEquals("GT001", detail.getTradeOrderNo());
        assertEquals("4407770000000001", detail.getCardNum());
        assertEquals("07", detail.getPayChannelCode());
        assertEquals("N", detail.getCompanionFlag());
        assertEquals("0441", detail.getTicketCode());
        assertEquals("3", detail.getCountingTimes());
        assertEquals("Y", detail.getCountingFlag());
        assertEquals("", detail.getDiscountFee());
        assertEquals("", detail.getDiscountInfo());
    }

    @Test
    void paySideFieldsComeFromPayTxnBrief() {
        when(gateTxnPayClient.queryByOrderNo("GT001")).thenReturn(order());
        when(alipayPaySignClient.queryPayTxnBrief(List.of("GT001"))).thenReturn(List.of(brief()));

        AlipayTripTravelDetailDTO detail = handler.findTravelDetail(request("GT001")).getData();

        assertEquals("2026091422001234567890", detail.getPayTradeOrderNo());
        assertEquals("20260914083012", detail.getPayOrderNoDate());
        assertEquals("1", detail.getInvoice());
    }


    @Test
    void payOrderNoDateIsNormalizedToFourteenDigits() {
        AlipayTripPayTxnBriefDTO withSeparators = brief();
        withSeparators.setTransTime("2026-09-18 16:44:41");
        when(gateTxnPayClient.queryByOrderNo("GT001")).thenReturn(order());
        when(alipayPaySignClient.queryPayTxnBrief(List.of("GT001"))).thenReturn(List.of(withSeparators));

        AlipayTripTravelDetailDTO detail = handler.findTravelDetail(request("GT001")).getData();

        assertEquals("20260918164441", detail.getPayOrderNoDate());
    }

    @Test
    void payOrderNoDateTooShortIsReturnedAsIs() {
        AlipayTripPayTxnBriefDTO epochMillis = brief();
        epochMillis.setTransTime("1785229085685");
        when(gateTxnPayClient.queryByOrderNo("GT001")).thenReturn(order());
        when(alipayPaySignClient.queryPayTxnBrief(List.of("GT001"))).thenReturn(List.of(epochMillis));

        AlipayTripTravelDetailDTO detail = handler.findTravelDetail(request("GT001")).getData();

        assertEquals("1785229085685", detail.getPayOrderNoDate());
    }


    @Test
    void debitRequestResultComesFromGateDebitStatusEvenWhenPayDetailSaysFail() {
        AlipayTripPayTxnBriefDTO failedAttempt = brief();
        failedAttempt.setPayStatus("FAIL");
        when(gateTxnPayClient.queryByOrderNo("GT001")).thenReturn(order());
        when(alipayPaySignClient.queryPayTxnBrief(List.of("GT001"))).thenReturn(List.of(failedAttempt));

        AlipayTripTravelDetailDTO detail = handler.findTravelDetail(request("GT001")).getData();

        assertEquals("0", detail.getDebitRequestResult());
    }

    @Test
    void missingBriefLeavesPaySideEmptyAndDebitResultStillFromGate() {
        GateTxnPayListDTO order = order();
        order.setDebitStatus("FAIL");
        when(gateTxnPayClient.queryByOrderNo("GT001")).thenReturn(order);
        when(alipayPaySignClient.queryPayTxnBrief(anyList())).thenReturn(List.of());

        AlipayTripTravelDetailDTO detail = handler.findTravelDetail(request("GT001")).getData();

        assertEquals("1", detail.getDebitRequestResult());
        assertNull(detail.getPayTradeOrderNo());
        assertNull(detail.getPayOrderNoDate());
        assertNull(detail.getInvoice());
    }

    @Test
    void missingOrderIsRejectedBeforeAnyPayTxnQuery() {
        when(gateTxnPayClient.queryByOrderNo("GT404")).thenReturn(null);

        AlipayTripFindTravelDetailRespDTO resp = handler.findTravelDetail(request("GT404"));

        assertEquals("9999", resp.getRetCode());
        assertEquals("支付订单不存在", resp.getRetMsg());
        assertNull(resp.getData());
        verify(alipayPaySignClient, never()).queryPayTxnBrief(any());
    }

    @Test
    void malformedIndustryDetailDoesNotFailTheRequest() {
        GateTxnPayListDTO order = order();
        order.setIndustryDetail("not-a-json");
        when(gateTxnPayClient.queryByOrderNo("GT001")).thenReturn(order);

        AlipayTripFindTravelDetailRespDTO resp = handler.findTravelDetail(request("GT001"));

        assertEquals("0000", resp.getRetCode());
        assertEquals("五四广场", resp.getData().getEntryStationName());
    }

    private AlipayTripFindTravelDetailReqDTO request(String orderNo) {
        AlipayTripFindTravelDetailReqDTO request = new AlipayTripFindTravelDetailReqDTO();
        request.setThirdUserId(THIRD_USER_ID);
        request.setOrderNo(orderNo);
        return request;
    }

    private GateTxnPayListDTO order() {
        GateTxnPayListDTO order = new GateTxnPayListDTO();
        order.setOrderNo("GT001");
        order.setCardId("4407770000000001");
        order.setDebitStatus("SUCCESS");
        order.setEntryStationName("五四广场");
        order.setInTime("20260914080000");
        order.setExitStationName("台东");
        order.setOutTime("20260914083000");
        order.setTrxAmount(200);
        order.setTotalAmount(500);
        order.setCompanionFlag("N");
        order.setTicketCode("0441");
        order.setCountingTimes(3);
        order.setCountingFlag("Y");
        order.setIssueChannelCode("07");
        order.setIndustryDetail("{\"orderNo\":\"GT001\"}");
        return order;
    }

    private AlipayTripPayTxnBriefDTO brief() {
        AlipayTripPayTxnBriefDTO brief = new AlipayTripPayTxnBriefDTO();
        brief.setOrderNo("GT001");
        brief.setPayStatus("SUCCESS");
        brief.setChannelOrderNo("2026091422001234567890");
        brief.setTransTime("20260914083012");
        brief.setInvoice("1");
        return brief;
    }
}
