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

/**
 * 支付宝出行行程详情的取数口径：<b>只用 {@code GATE_TXN_PAY} + {@code ALIPAY_PAY_TXN_DETAIL}</b>（2026-09-18 裁决）。
 *
 * <p>本类原在 {@code fep-alipay-server}（{@code TravelDetailFromGateTxnPayTest}），
 * 随实现于 1.0.5 迁入本模块 —— fep-alipay 那边现在只有一次 RPC 转发，没有可断言的编排逻辑。
 *
 * <p>改造前详情要对 ticket-server 发两次进出站明细查询、再按 500ms 超时合并，那套用例已整体作废：
 * <b>NEVER 据「行业明细里的 entryId / exitId 能切出 handleDateTime + trxType」再写回归用例</b>，
 * 现在 {@code industryDetail} 只解析用于日志、解析失败也不影响应答。
 *
 * <p><b>应答结构：业务字段全在 {@code data} 里</b>（2026-09-20 起，ADR-D150）。本类的断言一律走
 * {@code detail.getData().getXxx()}，<b>NEVER 改回从顶层取</b> —— 顶层只剩 {@code retCode} / {@code retMsg}。
 */
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
        // payAmount = 车费，totalAmount = 车费 + 超时费，NEVER 两者都填 TOTAL_AMOUNT。
        assertEquals("200", detail.getPayAmount());
        assertEquals("500", detail.getTotalAmount());
        assertEquals("0", detail.getOrderExpType());
        assertEquals("GT001", detail.getTradeOrderNo());
        assertEquals("4407770000000001", detail.getCardNum());
        assertEquals("07", detail.getPayChannelCode());
        // 这四个只有 GATE_TXN_PAY 有，进出站明细那边是错映射 / 压根没映射。
        assertEquals("N", detail.getCompanionFlag());
        assertEquals("0441", detail.getTicketCode());
        assertEquals("3", detail.getCountingTimes());
        assertEquals("Y", detail.getCountingFlag());
        // 两张表都没有渠道优惠列，恒为空串。
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

    /**
     * {@code payOrderNoDate} MUST 归一成 14 位 {@code yyyyMMddHHmmss}，与同一份应答里的进出站时间同格式。
     * {@code ALIPAY_PAY_TXN_DETAIL.TRANS_TIME} 存的是回调原文、格式不统一（2026-09-19 实测库内是 19 位带分隔符），
     * 原样透传会让支付宝侧按定长解析错位。
     */
    @Test
    void payOrderNoDateIsNormalizedToFourteenDigits() {
        AlipayTripPayTxnBriefDTO withSeparators = brief();
        withSeparators.setTransTime("2026-09-18 16:44:41");
        when(gateTxnPayClient.queryByOrderNo("GT001")).thenReturn(order());
        when(alipayPaySignClient.queryPayTxnBrief(List.of("GT001"))).thenReturn(List.of(withSeparators));

        AlipayTripTravelDetailDTO detail = handler.findTravelDetail(request("GT001")).getData();

        assertEquals("20260918164441", detail.getPayOrderNoDate());
    }

    /** 数字不足 14 位（如 13 位毫秒时间戳）一律原样返回，NEVER 补零或按时间戳换算成一个看着像时间的错值。 */
    @Test
    void payOrderNoDateTooShortIsReturnedAsIs() {
        AlipayTripPayTxnBriefDTO epochMillis = brief();
        epochMillis.setTransTime("1785229085685");
        when(gateTxnPayClient.queryByOrderNo("GT001")).thenReturn(order());
        when(alipayPaySignClient.queryPayTxnBrief(List.of("GT001"))).thenReturn(List.of(epochMillis));

        AlipayTripTravelDetailDTO detail = handler.findTravelDetail(request("GT001")).getData();

        assertEquals("1785229085685", detail.getPayOrderNoDate());
    }

    /**
     * debitRequestResult 只认 {@code GATE_TXN_PAY.DEBIT_STATUS}：支付明细那行是「某一次支付尝试」的结果，
     * 重试成功后主表已 SUCCESS 而旧明细仍可能 FAIL。NEVER 让 PAY_STATUS 覆盖它。
     */
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
        // 出站时间不是支付时间，查不到支付明细时 payOrderNoDate MUST 留空。
        assertNull(detail.getPayOrderNoDate());
        assertNull(detail.getInvoice());
    }

    /** 失败分支只填 retCode / retMsg，{@code data} MUST 为 null —— NEVER 塞空对象凑字段。 */
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
