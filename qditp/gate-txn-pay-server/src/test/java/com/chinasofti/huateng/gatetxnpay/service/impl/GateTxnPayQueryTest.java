package com.chinasofti.huateng.gatetxnpay.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.gatetxnpay.entity.GateTxnPay;
import com.chinasofti.huateng.gatetxnpay.mapper.GateTxnPayMapper;
import com.chinasofti.huateng.model.app.QueryActiveCardsReqDTO;
import com.chinasofti.huateng.model.app.QueryActiveCardsResult;
import com.chinasofti.huateng.model.app.RequestUserAccInfoReqDTO;
import com.chinasofti.huateng.model.app.RequestUserAccInfoResult;
import com.chinasofti.huateng.model.pay.GateTxnPayListDTO;
import com.chinasofti.huateng.model.ticket.QueryStatusReqDTO;
import com.chinasofti.huateng.model.ticket.QueryStatusRespDTO;
import com.chinasofti.huateng.rpc.account.AccountClient;
import com.chinasofti.huateng.rpc.ticket.TicketClient;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GateTxnPayQueryTest {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final GateTxnPayMapper mapper = mock(GateTxnPayMapper.class);
    private final AccountClient accountClient = mock(AccountClient.class);
    private final TicketClient ticketClient = mock(TicketClient.class);

    @Test
    void pageWithoutSearchScopeIsRejectedBeforeTouchingDb() {
        ResultVO<Map<String, Object>> result =
                service().page(null, null, null, "01", "04", "SUCCESS", null, null, null, 1, 10);
        assertEquals(ResultVO.ILLEGAL_PARAMS_CODE, result.getCode());
        verifyNoInteractions(mapper);
    }

    @Test
    void pageWithOnlyStartDateIsRejected() {
        ResultVO<Map<String, Object>> result =
                service().page(null, null, null, null, null, null, null, "20260901", null, 1, 10);
        assertEquals(ResultVO.ILLEGAL_PARAMS_CODE, result.getCode());
        verifyNoInteractions(mapper);
    }

    @Test
    void pageClampsPageSizeAndPageNum() {
        when(mapper.selectOperationPage(any(), any(), any(), any(), any(), any(), any(), any(), any(),
                anyInt(), anyInt())).thenReturn(List.of());
        when(mapper.countOperationPage(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(0);

        ArgumentCaptor<Integer> offset = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> limit = ArgumentCaptor.forClass(Integer.class);

        service().page(null, "C1", null, null, null, null, null, null, null, 0, 100000);
        verify(mapper).selectOperationPage(any(), any(), any(), any(), any(), any(), any(), any(), any(),
                offset.capture(), limit.capture());
        assertEquals(100, limit.getValue(), "pageSize MUST 被钳到 100");
        assertEquals(0, offset.getValue(), "pageNum<1 MUST 归一到第 1 页，offset=0");

        GateTxnPayMapper second = mock(GateTxnPayMapper.class);
        when(second.selectOperationPage(any(), any(), any(), any(), any(), any(), any(), any(), any(),
                anyInt(), anyInt())).thenReturn(List.of());
        ArgumentCaptor<Integer> offset2 = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> limit2 = ArgumentCaptor.forClass(Integer.class);
        service(second).page(null, "C1", null, null, null, null, null, null, null, 3, null);
        verify(second).selectOperationPage(any(), any(), any(), any(), any(), any(), any(), any(), any(),
                offset2.capture(), limit2.capture());
        assertEquals(10, limit2.getValue(), "pageSize 为空 MUST 回落默认 10");
        assertEquals(20, offset2.getValue(), "第 3 页 * 每页 10 MUST 得 offset=20");
    }

    @Test
    void userAccInfoRejectsMissingThirdUserId() {
        RequestUserAccInfoResult result = service().requestUserAccInfo(new RequestUserAccInfoReqDTO());
        assertEquals("9002", result.getRetCode());
        verifyNoInteractions(mapper);
    }

    @Test
    void userAccInfoBuildsStartDateFromConfiguredMonths() {
        RequestUserAccInfoResult counted = new RequestUserAccInfoResult();
        counted.setUnpaidCount(2);
        counted.setFailureCount(1);
        when(mapper.countUserAccInfo(anyString(), anyString())).thenReturn(counted);
        stubActiveCards();

        RequestUserAccInfoReqDTO request = new RequestUserAccInfoReqDTO();
        request.setThirdUserId("U1");

        GateTxnPayQueryServiceImpl service = service();
        setMonths(service, 6);
        RequestUserAccInfoResult result = service.requestUserAccInfo(request);

        assertEquals("0000", result.getRetCode());
        assertEquals(2, result.getUnpaidCount());
        assertEquals(1, result.getFailureCount());
        verify(mapper).countUserAccInfo("U1", LocalDate.now().minusMonths(6).format(YYYYMMDD));

        GateTxnPayMapper fallbackMapper = mock(GateTxnPayMapper.class);
        when(fallbackMapper.countUserAccInfo(anyString(), anyString())).thenReturn(counted);
        stubActiveCards();
        GateTxnPayQueryServiceImpl fallback = service(fallbackMapper);
        setMonths(fallback, 0);
        fallback.requestUserAccInfo(request);
        verify(fallbackMapper).countUserAccInfo("U1", LocalDate.now().minusMonths(3).format(YYYYMMDD));
    }

    @Test
    void userAccInfoNeverReportsSuccessWhenAggregateIsNull() {
        when(mapper.countUserAccInfo(anyString(), anyString())).thenReturn(null);
        RequestUserAccInfoReqDTO request = new RequestUserAccInfoReqDTO();
        request.setThirdUserId("U1");

        RequestUserAccInfoResult result = service().requestUserAccInfo(request);
        assertEquals("9002", result.getRetCode(), "查询未执行 MUST 用 retCode 表达，NEVER 返 0000");
        assertEquals("查询用户账务信息失败", result.getRetMsg());
    }

    @Test
    void listDtoFillsLegacyCountingColumns() {
        GateTxnPay record = new GateTxnPay();
        record.setOrderNo("GT20260913100000000123456");
        record.setDebitStatus("SUCCESS");
        record.setTrxAmount(400);
        record.setOvertimeAmount(0);
        record.setTotalAmount(400);
        record.setCountingTimes(null);
        record.setCountingFlag("  ");
        when(mapper.selectByOrderNo("GT20260913100000000123456")).thenReturn(record);

        GateTxnPayListDTO dto = service().selectByOrderNo("  GT20260913100000000123456  ");
        assertEquals("GT20260913100000000123456", dto.getOrderNo(), "订单号 MUST 先 trim 再查");
        assertEquals(0, dto.getCountingTimes());
        assertEquals("N", dto.getCountingFlag());
        assertEquals(400, dto.getTotalAmount());
    }

    @Test
    void selectByOrderNoShortCircuitsOnBlank() {
        assertNull(service().selectByOrderNo("   "));
        verifyNoInteractions(mapper);
    }

    @Test
    void userAccInfoAddsInProgressTrip() {
        RequestUserAccInfoResult counted = new RequestUserAccInfoResult();
        counted.setUnpaidCount(2);
        counted.setFailureCount(1);
        when(mapper.countUserAccInfo(anyString(), anyString())).thenReturn(counted);
        stubActiveCards("C1");
        when(ticketClient.queryQrCodeStatus(any(QueryStatusReqDTO.class))).thenAnswer(inv -> {
            QueryStatusReqDTO req = inv.getArgument(0);
            QueryStatusRespDTO resp = new QueryStatusRespDTO();
            resp.setRetCode("0000");
            resp.setCardId(req.getCardId());
            resp.setStatus("04");
            return resp;
        });

        RequestUserAccInfoReqDTO request = new RequestUserAccInfoReqDTO();
        request.setThirdUserId("U1");
        RequestUserAccInfoResult result = service().requestUserAccInfo(request);

        assertEquals("0000", result.getRetCode());
        assertEquals(3, result.getUnpaidCount(), "订单 2 笔 + 进行中行程 1 笔");
        assertEquals(1, result.getFailureCount(), "failureCount 不受聚合影响");
        verify(accountClient).queryActiveCards(any(QueryActiveCardsReqDTO.class));
    }

    @Test
    void userAccInfoCountsOnlyEnteredWithoutExitWhitelist() {
        RequestUserAccInfoResult counted = new RequestUserAccInfoResult();
        counted.setUnpaidCount(0);
        counted.setFailureCount(0);
        when(mapper.countUserAccInfo(anyString(), anyString())).thenReturn(counted);
        stubActiveCards("C-04", "C-81", "C-10", "C-05", "C-XX");
        when(ticketClient.queryQrCodeStatus(any(QueryStatusReqDTO.class))).thenAnswer(inv -> {
            QueryStatusReqDTO req = inv.getArgument(0);
            QueryStatusRespDTO resp = new QueryStatusRespDTO();
            resp.setRetCode("0000");
            resp.setCardId(req.getCardId());
            resp.setStatus(switch (req.getCardId()) {
                case "C-04" -> "04";
                case "C-81" -> "81";
                case "C-10" -> "10";
                case "C-05" -> "05";
                default -> "99";
            });
            return resp;
        });

        RequestUserAccInfoReqDTO request = new RequestUserAccInfoReqDTO();
        request.setThirdUserId("U1");
        RequestUserAccInfoResult result = service().requestUserAccInfo(request);

        assertEquals(3, result.getUnpaidCount(), "04 / 81 / 10 各 1 笔，05 / 99 不计");
    }

    @Test
    void userAccInfoTreatsQrCodeNotFoundAsNotInProgress() {
        RequestUserAccInfoResult counted = new RequestUserAccInfoResult();
        counted.setUnpaidCount(0);
        when(mapper.countUserAccInfo(anyString(), anyString())).thenReturn(counted);
        stubActiveCards("C1");
        stubStatus("C1", "8004", null);

        RequestUserAccInfoReqDTO request = new RequestUserAccInfoReqDTO();
        request.setThirdUserId("U1");
        RequestUserAccInfoResult result = service().requestUserAccInfo(request);

        assertEquals("0000", result.getRetCode());
        assertEquals(0, result.getUnpaidCount());
    }

    @Test
    void userAccInfoFailsClosedWhenAccountRpcFails() {
        RequestUserAccInfoResult counted = new RequestUserAccInfoResult();
        counted.setUnpaidCount(5);
        when(mapper.countUserAccInfo(anyString(), anyString())).thenReturn(counted);
        QueryActiveCardsResult failed = new QueryActiveCardsResult();
        failed.setRetCode("8001");
        when(accountClient.queryActiveCards(any(QueryActiveCardsReqDTO.class))).thenReturn(failed);

        RequestUserAccInfoReqDTO request = new RequestUserAccInfoReqDTO();
        request.setThirdUserId("U1");
        RequestUserAccInfoResult result = service().requestUserAccInfo(request);

        assertEquals("9002", result.getRetCode(), "聚合失败 MUST 用非成功码表达");
        assertEquals(0, result.getUnpaidCount(), "失败分支两个计数 MUST 为 0（primitive int 特性）");
    }

    @Test
    void userAccInfoFailsClosedWhenTicketRpcFails() {
        RequestUserAccInfoResult counted = new RequestUserAccInfoResult();
        counted.setUnpaidCount(1);
        when(mapper.countUserAccInfo(anyString(), anyString())).thenReturn(counted);
        stubActiveCards("C1");
        stubStatus("C1", "9999", null);

        RequestUserAccInfoReqDTO request = new RequestUserAccInfoReqDTO();
        request.setThirdUserId("U1");
        RequestUserAccInfoResult result = service().requestUserAccInfo(request);

        assertEquals("9002", result.getRetCode());
    }

    @Test
    void userAccInfoAllowsEmptyCardList() {
        RequestUserAccInfoResult counted = new RequestUserAccInfoResult();
        counted.setUnpaidCount(1);
        when(mapper.countUserAccInfo(anyString(), anyString())).thenReturn(counted);
        stubActiveCards();

        RequestUserAccInfoReqDTO request = new RequestUserAccInfoReqDTO();
        request.setThirdUserId("U1");
        RequestUserAccInfoResult result = service().requestUserAccInfo(request);

        assertEquals("0000", result.getRetCode());
        assertEquals(1, result.getUnpaidCount());
    }

    private void stubActiveCards(String... cardIds) {
        QueryActiveCardsResult cards = new QueryActiveCardsResult();
        cards.setRetCode("0000");
        List<String> list = new ArrayList<>();
        for (String cardId : cardIds) {
            list.add(cardId);
        }
        cards.setCardIds(list);
        when(accountClient.queryActiveCards(any(QueryActiveCardsReqDTO.class))).thenReturn(cards);
    }

    private void stubStatus(String cardId, String retCode, String status) {
        QueryStatusRespDTO resp = new QueryStatusRespDTO();
        resp.setRetCode(retCode);
        resp.setCardId(cardId);
        resp.setStatus(status);
        when(ticketClient.queryQrCodeStatus(org.mockito.ArgumentMatchers.argThat(
                r -> r != null && cardId.equals(r.getCardId())))).thenReturn(resp);
    }

    private GateTxnPayQueryServiceImpl service() {
        return service(mapper);
    }

    private GateTxnPayQueryServiceImpl service(GateTxnPayMapper gateTxnPayMapper) {
        return new GateTxnPayQueryServiceImpl(gateTxnPayMapper, accountClient, ticketClient);
    }

    private void setMonths(GateTxnPayQueryServiceImpl service, int months) {
        try {
            Field field = GateTxnPayQueryServiceImpl.class.getDeclaredField("accInfoQueryMonths");
            field.setAccessible(true);
            field.setInt(service, months);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("accInfoQueryMonths 字段名已变更", e);
        }
    }
}
