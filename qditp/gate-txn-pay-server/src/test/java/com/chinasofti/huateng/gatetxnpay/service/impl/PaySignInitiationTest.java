package com.chinasofti.huateng.gatetxnpay.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.gatetxnpay.entity.GateTxnPay;
import com.chinasofti.huateng.gatetxnpay.fare.FareCalculator;
import com.chinasofti.huateng.gatetxnpay.fare.FareDataGateway;
import com.chinasofti.huateng.gatetxnpay.mapper.GateTxnPayMapper;
import com.chinasofti.huateng.gatetxnpay.paysign.AlipayTripPayRequestFactory;
import com.chinasofti.huateng.gatetxnpay.paysign.GatePayRequestFactory;
import com.chinasofti.huateng.gatetxnpay.paysign.PayInitiationRpcAdapter;
import com.chinasofti.huateng.gatetxnpay.paysign.PaySignInitiator;
import com.chinasofti.huateng.gatetxnpay.station.StationNameBackfiller;
import com.chinasofti.huateng.gatetxnpay.writer.GateTxnPayWriter;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestPayReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestPayRespDTO;
import com.chinasofti.huateng.model.app.GatePayRequestDTO;
import com.chinasofti.huateng.model.app.RequestPayResult;
import com.chinasofti.huateng.rpc.alipay.paysign.AlipayPaySignClient;
import com.chinasofti.huateng.rpc.paySign.PaySignClient;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PaySignInitiationTest {

    private static final String OUT_TXN_DATE = "20260101";

    private static final String INDUSTRY_DETAIL =
            "{\"entryLineCode\":\"L1\",\"entryId\":\"E1\",\"cardIssueCode\":\"0007\"}";

    private final GateTxnPayMapper mapper = mock(GateTxnPayMapper.class);
    private final GateTxnPayWriter writer = mock(GateTxnPayWriter.class);
    private final FareCalculator fareCalculator = mock(FareCalculator.class);
    private final PaySignClient paySignClient = mock(PaySignClient.class);
    private final AlipayPaySignClient alipayPaySignClient = mock(AlipayPaySignClient.class);

    @Test
    void txnDateComesFromOrderNeverToday() {
        stubRetryable(order("GT10", 400, 0));
        when(paySignClient.requestPay(any())).thenReturn(payResult("0000"));

        service().retryPay("GT10");

        assertEquals(OUT_TXN_DATE, capturedRequest().getTxnDate(), "TXN_DATE MUST 取订单的出站日");
        assertNotEquals(LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE),
                capturedRequest().getTxnDate(), "TXN_DATE NEVER 取当日");
    }

    @Test
    void amountIsTotalAmountIncludingOvertimeFee() {
        stubRetryable(order("GT11", 400, 300));
        when(paySignClient.requestPay(any())).thenReturn(payResult("0000"));

        service().retryPay("GT11");

        assertEquals(700, capturedRequest().getAmount(), "MUST = 实扣 400 + 超时费 300");
    }

    @Test
    void configuredSceneAndTimeoutAreCarriedIntoTheRequest() {
        stubRetryable(order("GT12", 400, 0));
        when(paySignClient.requestPay(any())).thenReturn(payResult("0000"));

        service().retryPay("GT12");

        GatePayRequestDTO sent = capturedRequest();
        assertEquals("AGM_GATE", sent.getScene());
        assertEquals("1", sent.getIndustryType());
        assertEquals("地铁乘车扣费", sent.getSubject());
        assertEquals("地铁乘车费用", sent.getBody());
        assertEquals(60L, sent.getOrderTimeOut());
        assertEquals("GT12", sent.getOrderNo());
    }

    @Test
    void successRetCodeAdvancesToProcessing() {
        stubRetryable(order("GT13", 400, 0));
        when(paySignClient.requestPay(any())).thenReturn(payResult("0000"));

        assertEquals("PROCESSING", service().retryPay("GT13").getPayStatus());
        verify(writer).updateOrderStatusFromPending(any(), eq("PROCESSING"), any());
    }

    @Test
    void bizRejectedRetCodeLandsOnFailNeverRetry() {
        stubRetryable(order("GT14", 400, 0));
        when(paySignClient.requestPay(any())).thenReturn(payResult("9999"));

        assertEquals("FAIL", service().retryPay("GT14").getPayStatus());
        verify(writer).updateOrderStatusFromPending(any(), eq("FAIL"), contains("业务拒绝"));
    }

    @Test
    void nullResponseLandsOnFailWithChannelInReason() {
        stubRetryable(order("GT15", 400, 0));
        when(paySignClient.requestPay(any())).thenReturn(null);

        assertEquals("FAIL", service().retryPay("GT15").getPayStatus());
        verify(writer).updateOrderStatusFromPending(any(), eq("FAIL"), contains("pay-sign"));
    }

    @Test
    void paySignExceptionIsRecordedAsRetry() {
        stubPending(pendingOrder("GT16"));
        stubCalculated(400, 0);
        when(writer.applyOfflineFareRecalculated(any())).thenReturn(1);
        when(paySignClient.requestPay(any())).thenThrow(new IllegalStateException("Connection refused"));

        assertEquals(1, recoveryService().recoverOfflineFarePendingOrders(50, 7), "异常 NEVER 让这笔算作未推进");
        verify(writer).updateOrderStatusFromPending(any(), eq("RETRY"), contains("Connection refused"));
    }

    @Test
    void alipayChannelGoesToAlipayNeverToPaySign() {
        stubRetryable(alipayOrder("GT20"));
        when(alipayPaySignClient.alipayTripRequestPay(any())).thenReturn(alipayPayResult("0000"));

        service().retryPay("GT20");

        verify(alipayPaySignClient).alipayTripRequestPay(any());
        verifyNoInteractions(paySignClient);
        verify(writer).updateOrderStatusFromPending(any(), eq("PROCESSING"), anyString());
    }


    @Test
    void alipayIndustryDetailIsPassedThroughNeverRebuilt() {
        GateTxnPay order = alipayOrder("GT21");
        stubRetryable(order);
        when(alipayPaySignClient.alipayTripRequestPay(any())).thenReturn(alipayPayResult("0000"));

        service().retryPay("GT21");

        ArgumentCaptor<AlipayTripRequestPayReqDTO> sent =
                ArgumentCaptor.forClass(AlipayTripRequestPayReqDTO.class);
        verify(alipayPaySignClient).alipayTripRequestPay(sent.capture());
        JSONObject detail = JSON.parseObject(sent.getValue().getIndustryDetail());
        assertEquals("L1", detail.getString("entryLineCode"), "快照键 MUST 原样透传，NEVER 重算");
        assertEquals("E1", detail.getString("entryId"));
        assertEquals("0007", detail.getString("cardIssueCode"));
        assertEquals(AlipayTripPayRequestFactory.PAY_TRIGGER_COMPENSATION, detail.getString("payTriggerType"),
                "retryPay 是补偿重推，MUST 标 02-补偿支付");
        assertEquals("GT21", sent.getValue().getOrderNo(), "订单号 MUST 取 GATE_TXN_PAY.ORDER_NO");
        assertEquals("T21", sent.getValue().getRequestSignSeq(), "requestSignSeq MUST 取 TICKET_TRANS_SEQ");
        assertEquals(700, sent.getValue().getAmount(), "金额 MUST 是 TOTAL_AMOUNT（实扣 + 超时费）");
    }

    @Test
    void firstInitiationMarksPayTriggerType01() {
        when(alipayPaySignClient.alipayTripRequestPay(any())).thenReturn(alipayPayResult("0000"));

        initiator().initiateAsync(alipayOrder("GT30"), null);

        ArgumentCaptor<AlipayTripRequestPayReqDTO> sent =
                ArgumentCaptor.forClass(AlipayTripRequestPayReqDTO.class);
        verify(alipayPaySignClient).alipayTripRequestPay(sent.capture());
        assertEquals(AlipayTripPayRequestFactory.PAY_TRIGGER_FIRST,
                JSON.parseObject(sent.getValue().getIndustryDetail()).getString("payTriggerType"));
    }

    @Test
    void explicitFirstRetryMarksPayTriggerType01() {
        when(alipayPaySignClient.alipayTripRequestPay(any())).thenReturn(alipayPayResult("0000"));

        initiator().retryAndConverge(alipayOrder("GT31"), AlipayTripPayRequestFactory.PAY_TRIGGER_FIRST);

        ArgumentCaptor<AlipayTripRequestPayReqDTO> sent =
                ArgumentCaptor.forClass(AlipayTripRequestPayReqDTO.class);
        verify(alipayPaySignClient).alipayTripRequestPay(sent.capture());
        assertEquals(AlipayTripPayRequestFactory.PAY_TRIGGER_FIRST,
                JSON.parseObject(sent.getValue().getIndustryDetail()).getString("payTriggerType"));
    }

    @Test
    void alipayBizRejectedLandsOnFailWithAlipayChannelInReason() {
        stubRetryable(alipayOrder("GT22"));
        when(alipayPaySignClient.alipayTripRequestPay(any())).thenReturn(alipayPayResult("9999"));

        service().retryPay("GT22");

        verify(writer).updateOrderStatusFromPending(any(), eq("FAIL"), contains("alipay-pay-sign"));
    }

    @Test
    void nonAlipayChannelNeverTouchesAlipay() {
        stubRetryable(order("GT23", 400, 0));
        when(paySignClient.requestPay(any())).thenReturn(payResult("0000"));

        service().retryPay("GT23");

        verifyNoInteractions(alipayPaySignClient);
    }


    @Test
    void paySignChannelPassesSnapshotThroughWithTriggerInjected() {
        GateTxnPay order = order("GT24", 400, 0);
        order.setIndustryDetail(INDUSTRY_DETAIL);
        stubRetryable(order);
        when(paySignClient.requestPay(any())).thenReturn(payResult("0000"));

        service().retryPay("GT24");

        JSONObject detail = JSON.parseObject(capturedRequest().getIndustryDetail());
        assertEquals("L1", detail.getString("entryLineCode"), "快照键 MUST 原样透传，NEVER 重算");
        assertEquals("E1", detail.getString("entryId"));
        assertEquals("0007", detail.getString("cardIssueCode"));
        assertEquals(AlipayTripPayRequestFactory.PAY_TRIGGER_COMPENSATION, detail.getString("payTriggerType"),
                "retryPay 是补偿重推，MUST 标 02-补偿支付");
    }

    @Test
    void paySignChannelFallsBackToEntityDumpWhenSnapshotMissing() {
        stubRetryable(order("GT25", 400, 0));
        when(paySignClient.requestPay(any())).thenReturn(payResult("0000"));

        service().retryPay("GT25");

        JSONObject detail = JSON.parseObject(capturedRequest().getIndustryDetail());
        assertEquals("GT25", detail.getString("orderNo"), "实体兜底保留排查能力");
        assertEquals(AlipayTripPayRequestFactory.PAY_TRIGGER_COMPENSATION, detail.getString("payTriggerType"));
    }

    private AlipayTripRequestPayRespDTO alipayPayResult(String retCode) {
        AlipayTripRequestPayRespDTO result = new AlipayTripRequestPayRespDTO();
        result.setRetCode(retCode);
        result.setRetMsg("retCode=" + retCode);
        return result;
    }

    private GateTxnPay alipayOrder(String orderNo) {
        GateTxnPay order = order(orderNo, 400, 300);
        order.setIssueChannelCode("07");
        order.setTicketTransSeq("T" + orderNo.substring(2));
        order.setIndustryDetail(INDUSTRY_DETAIL);
        return order;
    }

    private GatePayRequestDTO capturedRequest() {
        ArgumentCaptor<GatePayRequestDTO> sent = ArgumentCaptor.forClass(GatePayRequestDTO.class);
        verify(paySignClient).requestPay(sent.capture());
        return sent.getValue();
    }

    private RequestPayResult payResult(String retCode) {
        RequestPayResult result = new RequestPayResult();
        result.setRetCode(retCode);
        result.setRetMsg("retCode=" + retCode);
        return result;
    }

    private void stubRetryable(GateTxnPay order) {
        when(mapper.selectByOrderNo(order.getOrderNo())).thenReturn(order);
    }

    private void stubPending(GateTxnPay pending) {
        when(mapper.selectOfflineFarePending(anyString(), anyString(), anyInt())).thenReturn(List.of(pending));
    }

    private void stubCalculated(int trxAmount, int overtimeAmount) {
        doAnswer(invocation -> {
            GateTxnPay order = invocation.getArgument(0);
            order.setTrxAmount(trxAmount);
            order.setOvertimeAmount(overtimeAmount);
            return null;
        }).when(fareCalculator).calculateOfflineFare(any(), any());
    }

    private GateTxnPay order(String orderNo, int trxAmount, int overtimeAmount) {
        GateTxnPay order = base(orderNo);
        order.setDebitStatus("RETRY");
        order.setTrxAmount(trxAmount);
        order.setOvertimeAmount(overtimeAmount);
        order.setTotalAmount(trxAmount + overtimeAmount);
        return order;
    }

    private GateTxnPay pendingOrder(String orderNo) {
        GateTxnPay order = base(orderNo);
        order.setDebitStatus("INIT");
        order.setTrxAmount(0);
        order.setOvertimeAmount(0);
        order.setTotalAmount(0);
        order.setOfflineFlag("Y");
        return order;
    }

    private GateTxnPay base(String orderNo) {
        GateTxnPay order = new GateTxnPay();
        order.setOrderNo(orderNo);
        order.setTxnDate(OUT_TXN_DATE);
        order.setCardId("C1");
        order.setCardType("04");
        order.setThirdUserId("U1");
        order.setTrxType("02");
        order.setInStation("0101");
        order.setOutStation("0110");
        order.setPaymentVendor("0B");
        order.setPayUserId("P1");
        return order;
    }

    private GateTxnPayServiceImpl service() {
        return new GateTxnPayServiceImpl(
                mapper, writer, fareCalculator, initiator(),
                new MetroTransferPushTaskProcessor(null, null, false, 0, 0, 0L),
                new GateTxnPayManualOpsService(mapper, paySignClient, alipayPaySignClient, initiator()),
                noopStationNameBackfiller(),
                null,
                null);
    }


    private StationNameBackfiller noopStationNameBackfiller() {
        return new StationNameBackfiller(new FareDataGateway(null, null, null, null, null, null) {
            @Override
            public Map<String, String> resolveStationNamesQuietly(Set<String> stationCodes) {
                return Collections.emptyMap();
            }
        });
    }

    private PaySignInitiator initiator() {
        return new PaySignInitiator(writer,
                new PayInitiationRpcAdapter(paySignClient, alipayPaySignClient),
                new GatePayRequestFactory("AGM_GATE", "1", "地铁乘车扣费", "地铁乘车费用", 60L),
                new AlipayTripPayRequestFactory("TRIP", "05", "1", "地铁乘车扣费", "地铁乘车费用", 60),
                null);
    }

    private OfflineFareRecoveryServiceImpl recoveryService() {
        return new OfflineFareRecoveryServiceImpl(
                mapper, writer, fareCalculator, initiator(),
                new MetroTransferPushTaskProcessor(null, null, false, 0, 0, 0L),
                noopStationNameBackfiller());
    }
}
