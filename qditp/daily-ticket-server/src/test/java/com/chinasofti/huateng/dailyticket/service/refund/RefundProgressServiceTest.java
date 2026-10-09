package com.chinasofti.huateng.dailyticket.service.refund;

import com.chinasofti.huateng.dailyticket.client.DailyTicketPayGatewayClient;
import com.chinasofti.huateng.dailyticket.client.DailyTicketPayGatewayResponse;
import com.chinasofti.huateng.dailyticket.config.DailyTicketPayProperties;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketInstanceMapper;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketOrderMapper;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketRefundMapper;
import com.chinasofti.huateng.dailyticket.mapper.TravelTicketOrderMapper;
import com.chinasofti.huateng.dailyticket.model.DailyTicketInstance;
import com.chinasofti.huateng.dailyticket.model.DailyTicketOrder;
import com.chinasofti.huateng.dailyticket.model.DailyTicketRefund;
import com.chinasofti.huateng.dailyticket.service.paylog.DailyTicketPayLogWriter;
import com.chinasofti.huateng.dailyticket.service.support.DailyTicketInstanceStatus;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketOrderNoReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketRefundResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 钉住退款进度推进（回查 / 重试 / 重提交）的三条不可回退口径。
 *
 * <p>最值钉的是**「平台返未知状态时保持处理中」**与**「重试前先回查」**：前者写错会把在途退款错判成失败并
 * 允许再发一笔，后者跳过会在支付中心已成功的单上重复出网 —— 两者都只在真发起退款时才走到，只读回归测不出来。
 */
class RefundProgressServiceTest {

    private DailyTicketOrderMapper orderMapper;
    private TravelTicketOrderMapper travelOrderMapper;
    private DailyTicketRefundMapper refundMapper;
    private DailyTicketInstanceMapper instanceMapper;
    private DailyTicketPayGatewayClient payGatewayClient;
    private DailyTicketPayLogWriter payLogWriter;
    private DailyTicketRefundSettlementService refundSettlementService;
    private RefundProgressService service;

    @BeforeEach
    void setUp() {
        orderMapper = mock(DailyTicketOrderMapper.class);
        travelOrderMapper = mock(TravelTicketOrderMapper.class);
        refundMapper = mock(DailyTicketRefundMapper.class);
        instanceMapper = mock(DailyTicketInstanceMapper.class);
        payGatewayClient = mock(DailyTicketPayGatewayClient.class);
        payLogWriter = mock(DailyTicketPayLogWriter.class);
        refundSettlementService = mock(DailyTicketRefundSettlementService.class);
        service = new RefundProgressService(orderMapper, travelOrderMapper, refundMapper, instanceMapper,
                payGatewayClient, payLogWriter, refundSettlementService,
                new RefundGatewayRequests(mock(DailyTicketPayProperties.class)));
    }

    private DailyTicketOrderNoReqDTO request() {
        DailyTicketOrderNoReqDTO request = new DailyTicketOrderNoReqDTO();
        request.setOrderNo("0E01");
        request.setOrderType("1");
        return request;
    }

    private DailyTicketOrder order() {
        DailyTicketOrder order = new DailyTicketOrder();
        order.setOrderNo("0E01");
        order.setPaymentOrderNo("PAY-1");
        order.setPayChannelCode("01");
        return order;
    }

    private DailyTicketRefund refunding() {
        DailyTicketRefund refund = new DailyTicketRefund();
        refund.setOrderNo("0E01");
        refund.setRefundOrderNo("RF-1");
        refund.setRefundStatus("REFUNDING");
        refund.setRefundType("00");
        refund.setRefundAmount(100);
        refund.setPlatformRefundNo("PF-1");
        return refund;
    }

    private DailyTicketPayGatewayResponse okWith(Map<String, Object> data) {
        DailyTicketPayGatewayResponse response = new DailyTicketPayGatewayResponse();
        response.setCode(0);
        response.setData(data);
        return response;
    }

    private Map<String, Object> statusData(String status) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", status);
        return data;
    }

    @Test
    void 回查白名单外状态一律幂等返当前进度并不调网关() {
        DailyTicketRefund refund = refunding();
        refund.setRefundStatus("REFUNDED");
        when(orderMapper.selectByOrderNo("0E01")).thenReturn(order());
        when(refundMapper.selectByOrderNo("0E01")).thenReturn(refund);

        DailyTicketRefundResult result = service.queryRefundTicket(request());

        assertEquals("0000", result.getRetCode());
        assertEquals("SUCCESS", result.getRefundResult());
        verify(payGatewayClient, never()).requestRefundQuery(any());
    }

    @Test
    void 回查在平台退款单号缺失时拒绝() {
        DailyTicketRefund refund = refunding();
        refund.setPlatformRefundNo(null);
        when(orderMapper.selectByOrderNo("0E01")).thenReturn(order());
        when(refundMapper.selectByOrderNo("0E01")).thenReturn(refund);

        DailyTicketRefundResult result = service.queryRefundTicket(request());

        assertEquals("9999", result.getRetCode());
        verify(payGatewayClient, never()).requestRefundQuery(any());
    }

    @Test
    void 回查遇未知平台状态时保持处理中不推进状态() {
        when(orderMapper.selectByOrderNo("0E01")).thenReturn(order());
        when(refundMapper.selectByOrderNo("0E01")).thenReturn(refunding());
        when(payGatewayClient.requestRefundQuery(any())).thenReturn(okWith(statusData("WHATEVER")));

        DailyTicketRefundResult result = service.queryRefundTicket(request());

        assertEquals("0000", result.getRetCode());
        assertEquals("PROCESSING", result.getRefundResult());
        verify(refundSettlementService, never()).markRefunded(any(), any(), any());
        verify(refundSettlementService, never()).markRefundFailed(any(), any(), any());
    }

    @Test
    void 回查遇平台明确失败时走收口置失败() {
        Map<String, Object> data = statusData("FAIL");
        when(orderMapper.selectByOrderNo("0E01")).thenReturn(order());
        when(refundMapper.selectByOrderNo("0E01")).thenReturn(refunding());
        when(payGatewayClient.requestRefundQuery(any())).thenReturn(okWith(data));

        DailyTicketRefundResult result = service.queryRefundTicket(request());

        assertEquals("0000", result.getRetCode());
        assertEquals("FAILED", result.getRefundResult());
        verify(refundSettlementService).markRefundFailed(any(), any(), eq(data));
    }

    @Test
    void 回查遇平台成功时走收口置已退款() {
        Map<String, Object> data = statusData("SUCCESS");
        when(orderMapper.selectByOrderNo("0E01")).thenReturn(order());
        when(refundMapper.selectByOrderNo("0E01")).thenReturn(refunding());
        when(payGatewayClient.requestRefundQuery(any())).thenReturn(okWith(data));

        DailyTicketRefundResult result = service.queryRefundTicket(request());

        assertEquals("0000", result.getRetCode());
        verify(refundSettlementService).markRefunded(any(), any(), eq(data));
        verify(payLogWriter).insert(eq("0E01"), eq("REFUND_QUERY"), eq("01"), any(), any());
    }

    @Test
    void 重试在回查失败时直接返回回查结果不重发网关() {
        DailyTicketRefund refund = refunding();
        refund.setPlatformRefundNo(null);
        when(orderMapper.selectByOrderNo("0E01")).thenReturn(order());
        when(refundMapper.selectByOrderNo("0E01")).thenReturn(refund);

        DailyTicketRefundResult result = service.retryRefundTicket(request());

        assertEquals("9999", result.getRetCode());
        verify(payGatewayClient, never()).requestRefund(any());
    }

    @Test
    void 核验退款不允许走平台重试() {
        DailyTicketRefund refund = refunding();
        refund.setRefundType("01");
        when(orderMapper.selectByOrderNo("0E01")).thenReturn(order());
        when(refundMapper.selectByOrderNo("0E01")).thenReturn(refund);

        DailyTicketRefundResult result = service.retryRefundTicket(request());

        assertEquals("9999", result.getRetCode());
        verify(payGatewayClient, never()).requestRefundQuery(any());
        verify(payGatewayClient, never()).requestRefund(any());
    }

    @Test
    void 重试在回查为处理中时重发并按退款时间收口() {
        Map<String, Object> retryData = new LinkedHashMap<>();
        retryData.put("refundTime", "20260921103000");
        when(orderMapper.selectByOrderNo("0E01")).thenReturn(order());
        when(refundMapper.selectByOrderNo("0E01")).thenReturn(refunding());
        when(payGatewayClient.requestRefundQuery(any())).thenReturn(okWith(statusData("PROCESSING")));
        when(payGatewayClient.requestRefund(any())).thenReturn(okWith(retryData));

        DailyTicketRefundResult result = service.retryRefundTicket(request());

        assertEquals("0000", result.getRetCode());
        verify(refundSettlementService).markRefunded(any(), any(), eq(retryData));
        verify(payLogWriter).insert(eq("0E01"), eq("REFUND_RETRY"), eq("01"), any(), any());
    }

    @Test
    void 重提交在核验退款且票不在退款锁定态时拒绝放款() {
        DailyTicketRefund refund = refunding();
        refund.setRefundType("01");
        refund.setPlatformRefundNo(null);
        DailyTicketInstance ticket = new DailyTicketInstance();
        ticket.setTicketStatus("ACTIVATED");
        when(orderMapper.selectByOrderNo("0E01")).thenReturn(order());
        when(refundMapper.selectByOrderNo("0E01")).thenReturn(refund);
        when(instanceMapper.selectByOrderNo("0E01")).thenReturn(ticket);

        DailyTicketRefundResult result = service.resubmitRefundTicket(request());

        assertEquals("9999", result.getRetCode());
        verify(payGatewayClient, never()).requestRefund(any());
    }

    @Test
    void 重提交在观察期未满时拒绝() {
        DailyTicketRefund refund = refunding();
        refund.setRefundStatus("WAIT_VERIFY");
        refund.setRefundType("01");
        refund.setVerifyAfterTime(new Date(System.currentTimeMillis() + 600_000L));
        when(orderMapper.selectByOrderNo("0E01")).thenReturn(order());
        when(refundMapper.selectByOrderNo("0E01")).thenReturn(refund);

        DailyTicketRefundResult result = service.resubmitRefundTicket(request());

        assertEquals("9999", result.getRetCode());
        verify(instanceMapper, never()).selectByOrderNo(anyString());
        verify(payGatewayClient, never()).requestRefund(any());
    }

    @Test
    void 重提交在平台已受理时拒绝并引导走回查() {
        when(orderMapper.selectByOrderNo("0E01")).thenReturn(order());
        when(refundMapper.selectByOrderNo("0E01")).thenReturn(refunding());

        DailyTicketRefundResult result = service.resubmitRefundTicket(request());

        assertEquals("9999", result.getRetCode());
        verify(payGatewayClient, never()).requestRefund(any());
    }

    @Test
    void 重提交成功且未带退款时间时只回填平台退款单号() {
        DailyTicketRefund refund = refunding();
        refund.setPlatformRefundNo(null);
        when(orderMapper.selectByOrderNo("0E01")).thenReturn(order());
        when(refundMapper.selectByOrderNo("0E01")).thenReturn(refund);
        when(payGatewayClient.requestRefund(any())).thenReturn(okWith(new LinkedHashMap<>()));

        DailyTicketRefundResult result = service.resubmitRefundTicket(request());

        assertEquals("0000", result.getRetCode());
        assertEquals("PROCESSING", result.getRefundResult());
        verify(refundSettlementService).updatePlatformRefundNo(any(), any());
        verify(refundSettlementService).markRefunding(any(), any());
        verify(refundSettlementService, never()).markRefunded(any(), any(), any());
        verify(payLogWriter).insert(eq("0E01"), eq("REFUND_RESUBMIT"), eq("01"), any(), any());
    }

    @Test
    void 重提交在核验退款且票已锁定时放行到网关() {
        DailyTicketRefund refund = refunding();
        refund.setRefundType("01");
        refund.setPlatformRefundNo(null);
        DailyTicketInstance ticket = new DailyTicketInstance();
        ticket.setTicketStatus(DailyTicketInstanceStatus.REFUND_LOCKED);
        when(orderMapper.selectByOrderNo("0E01")).thenReturn(order());
        when(refundMapper.selectByOrderNo("0E01")).thenReturn(refund);
        when(instanceMapper.selectByOrderNo("0E01")).thenReturn(ticket);
        when(payGatewayClient.requestRefund(any())).thenReturn(okWith(new LinkedHashMap<>()));

        DailyTicketRefundResult result = service.resubmitRefundTicket(request());

        assertEquals("0000", result.getRetCode());
        verify(payGatewayClient).requestRefund(any());
    }
}
