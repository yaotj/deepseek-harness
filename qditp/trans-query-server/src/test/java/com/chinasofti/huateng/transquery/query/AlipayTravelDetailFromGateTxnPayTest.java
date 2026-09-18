package com.chinasofti.huateng.transquery.query;

import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailRespVO;
import com.chinasofti.huateng.model.pay.GateTxnPayListDTO;
import com.chinasofti.huateng.rpc.alipay.paysign.AlipayPaySignClient;
import com.chinasofti.huateng.rpc.pay.GateTxnPayClient;
import com.chinasofti.huateng.rpc.ticket.TicketClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 支付宝出行行程详情改读 {@code GATE_TXN_PAY} 的判据。
 *
 * <p>本类原在 {@code fep-alipay-server}（{@code TravelDetailFromGateTxnPayTest}），
 * 随实现于 1.0.5 迁入本模块 —— fep-alipay 那边现在只有一次 RPC 转发，没有可断言的编排逻辑。
 */
class AlipayTravelDetailFromGateTxnPayTest {

    private static final String THIRD_USER_ID = "2088";
    private static final String ENTRY_ID = "20882026091408000001";
    private static final String EXIT_ID = "20882026091408300002";

    private GateTxnPayClient gateTxnPayClient;
    private TicketClient ticketClient;
    private AlipayTravelQueryHandler handler;

    @BeforeEach
    void setUp() {
        gateTxnPayClient = mock(GateTxnPayClient.class);
        ticketClient = mock(TicketClient.class);
        handler = new AlipayTravelQueryHandler(mock(AlipayPaySignClient.class), ticketClient, gateTxnPayClient);
    }

    @Test
    void entryAndExitIdComeFromIndustryDetail() {
        when(gateTxnPayClient.queryByOrderNo("GT001")).thenReturn(order("{\"entryId\":\"" + ENTRY_ID
                + "\",\"exitId\":\"" + EXIT_ID + "\",\"orderNo\":\"GT001\"}"));
        when(ticketClient.alipayTripFindTravelDetail(any())).thenReturn(ticketSuccess());

        AlipayTripFindTravelDetailRespVO vo = handler.findTravelDetail(request("GT001"));

        assertEquals("0000", vo.getRetCode());
        ArgumentCaptor<AlipayTripFindTravelDetailReqDTO> captor =
                ArgumentCaptor.forClass(AlipayTripFindTravelDetailReqDTO.class);
        verify(ticketClient, org.mockito.Mockito.times(2)).alipayTripFindTravelDetail(captor.capture());
        List<AlipayTripFindTravelDetailReqDTO> sent = captor.getAllValues();
        // 进出站两次查询是并行发出的，到达顺序不确定，只钉「两次都带对了卡号，且进出站各一次」。
        assertEquals(2, sent.size());
        sent.forEach(req -> assertEquals("4407770000000001", req.getCardId()));
        Set<String> trxTypes = sent.stream()
                .map(AlipayTripFindTravelDetailReqDTO::getTrxType)
                .collect(Collectors.toSet());
        assertEquals(Set.of("01", "02"), trxTypes);
        Set<String> handleDateTimes = sent.stream()
                .map(AlipayTripFindTravelDetailReqDTO::getHandleDateTime)
                .collect(Collectors.toSet());
        assertEquals(Set.of("20260914080000", "20260914083000"), handleDateTimes);
    }

    @Test
    void orderFieldsAreMappedAndChannelTradeNoStaysNull() {
        when(gateTxnPayClient.queryByOrderNo("GT001")).thenReturn(order("{\"entryId\":\"" + ENTRY_ID
                + "\",\"exitId\":\"" + EXIT_ID + "\"}"));
        when(ticketClient.alipayTripFindTravelDetail(any())).thenReturn(ticketSuccess());

        AlipayTripFindTravelDetailRespDTO detail = handler.findTravelDetail(request("GT001")).getData();

        assertEquals("GT001", detail.getTradeOrderNo());
        assertEquals("4407770000000001", detail.getCardNum());
        assertEquals("0", detail.getDebitRequestResult());
        assertEquals("20260914083000", detail.getPayOrderNoDate());
        assertEquals("07", detail.getPayChannelCode());
        assertNull(detail.getPayTradeOrderNo());
        assertNull(detail.getInvoice());
    }

    @Test
    void failedDebitStatusIsMappedToOne() {
        GateTxnPayListDTO order = order("{\"entryId\":\"" + ENTRY_ID + "\"}");
        order.setDebitStatus("FAIL");
        when(gateTxnPayClient.queryByOrderNo("GT001")).thenReturn(order);
        when(ticketClient.alipayTripFindTravelDetail(any())).thenReturn(ticketSuccess());

        assertEquals("1", handler.findTravelDetail(request("GT001")).getData().getDebitRequestResult());
    }

    @Test
    void missingOrderIsRejectedBeforeAnyTicketQuery() {
        when(gateTxnPayClient.queryByOrderNo("GT404")).thenReturn(null);

        AlipayTripFindTravelDetailRespVO vo = handler.findTravelDetail(request("GT404"));

        assertEquals("9999", vo.getRetCode());
        verify(ticketClient, never()).alipayTripFindTravelDetail(any());
    }

    @Test
    void malformedIndustryDetailDoesNotThrowAndSkipsTicketQuery() {
        when(gateTxnPayClient.queryByOrderNo("GT001")).thenReturn(order("not-a-json"));

        AlipayTripFindTravelDetailRespVO vo = handler.findTravelDetail(request("GT001"));

        assertEquals("9999", vo.getRetCode());
        assertEquals("未查询到行程数据", vo.getRetMsg());
        verify(ticketClient, never()).alipayTripFindTravelDetail(any());
    }

    private AlipayTripFindTravelDetailReqDTO request(String orderNo) {
        AlipayTripFindTravelDetailReqDTO request = new AlipayTripFindTravelDetailReqDTO();
        request.setThirdUserId(THIRD_USER_ID);
        request.setOrderNo(orderNo);
        return request;
    }

    private GateTxnPayListDTO order(String industryDetail) {
        GateTxnPayListDTO order = new GateTxnPayListDTO();
        order.setOrderNo("GT001");
        order.setCardId("4407770000000001");
        order.setDebitStatus("SUCCESS");
        order.setOutTime("20260914083000");
        order.setIssueChannelCode("07");
        order.setIndustryDetail(industryDetail);
        return order;
    }

    private AlipayTripFindTravelDetailRespDTO ticketSuccess() {
        AlipayTripFindTravelDetailRespDTO resp = new AlipayTripFindTravelDetailRespDTO();
        resp.setRetCode("0000");
        resp.setEntryStationName("五四广场");
        resp.setEntryDate("20260914080000");
        return resp;
    }
}
