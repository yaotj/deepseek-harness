package com.chinasofti.huateng.dailyticket.service.refund;

import com.chinasofti.huateng.dailyticket.client.DailyTicketPayGatewayClient;
import com.chinasofti.huateng.dailyticket.client.DailyTicketPayGatewayResponse;
import com.chinasofti.huateng.dailyticket.config.DailyTicketPayProperties;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketInstanceMapper;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketOrderMapper;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketRefundDetailMapper;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketRefundMapper;
import com.chinasofti.huateng.dailyticket.mapper.TravelTicketOrderMapper;
import com.chinasofti.huateng.dailyticket.model.DailyTicketInstance;
import com.chinasofti.huateng.dailyticket.model.DailyTicketOrder;
import com.chinasofti.huateng.dailyticket.model.DailyTicketRefund;
import com.chinasofti.huateng.dailyticket.model.TravelTicketOrder;
import com.chinasofti.huateng.dailyticket.page.TravelTicketSubRefundRequest;
import com.chinasofti.huateng.dailyticket.service.paylog.DailyTicketPayLogWriter;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketRefundResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 钉住旅游票子单退款发起的分支：六道前置校验、小程序渠道不调网关、网关三态（成功 / 明确失败 / 抛异常）。
 *
 * <p>最需要钉住的是**网关明确失败时必须 `releaseLock`** —— 不释放票锁那张票就永久停在
 * {@code REFUND_LOCKED}，既不能乘也不能再退，且只在真发起一笔退款时才走到这条分支、只读回归测不出来。
 */
class TravelSubRefundServiceTest {

    private TravelTicketOrderMapper travelOrderMapper;
    private DailyTicketOrderMapper orderMapper;
    private DailyTicketRefundMapper refundMapper;
    private DailyTicketRefundDetailMapper refundDetailMapper;
    private DailyTicketInstanceMapper instanceMapper;
    private DailyTicketPayGatewayClient payGatewayClient;
    private DailyTicketPayProperties payProperties;
    private DailyTicketPayLogWriter payLogWriter;
    private DailyTicketTicketLockWriter ticketLockWriter;
    private DailyTicketRefundSettlementService refundSettlementService;
    private TravelSubRefundService service;

    @BeforeEach
    void setUp() {
        travelOrderMapper = mock(TravelTicketOrderMapper.class);
        orderMapper = mock(DailyTicketOrderMapper.class);
        refundMapper = mock(DailyTicketRefundMapper.class);
        refundDetailMapper = mock(DailyTicketRefundDetailMapper.class);
        instanceMapper = mock(DailyTicketInstanceMapper.class);
        payGatewayClient = mock(DailyTicketPayGatewayClient.class);
        payProperties = mock(DailyTicketPayProperties.class);
        payLogWriter = mock(DailyTicketPayLogWriter.class);
        ticketLockWriter = mock(DailyTicketTicketLockWriter.class);
        refundSettlementService = mock(DailyTicketRefundSettlementService.class);
        service = new TravelSubRefundService(travelOrderMapper, orderMapper, refundMapper, refundDetailMapper,
                instanceMapper, payGatewayClient, payLogWriter, ticketLockWriter, refundSettlementService,
                new RefundGatewayRequests(payProperties));
    }

    private TravelTicketSubRefundRequest request() {
        TravelTicketSubRefundRequest request = new TravelTicketSubRefundRequest();
        request.setParentOrderNo("0T01");
        request.setSubOrderNo("0E01");
        return request;
    }

    private TravelTicketOrder payableParent() {
        TravelTicketOrder parent = new TravelTicketOrder();
        parent.setOrderNo("0T01");
        parent.setPayStatus("PAID");
        parent.setOrderStatus("PAID");
        parent.setPaymentOrderNo("PAY-1");
        parent.setPayChannelCode("01");
        return parent;
    }

    private DailyTicketOrder child() {
        DailyTicketOrder child = new DailyTicketOrder();
        child.setOrderNo("0E01");
        child.setParentOrderNo("0T01");
        child.setTicketPrice(100);
        return child;
    }

    private void stubPayableRelation() {
        when(travelOrderMapper.selectByOrderNo("0T01")).thenReturn(payableParent());
        when(orderMapper.selectByOrderNo("0E01")).thenReturn(child());
        DailyTicketInstance ticket = new DailyTicketInstance();
        ticket.setId("INST-1");
        ticket.setTicketStatus("ACTIVATED");
        when(instanceMapper.selectByOrderNo("0E01")).thenReturn(ticket);
        when(ticketLockWriter.lockForRefund(any())).thenReturn(true);
    }

    @Test
    void 主子单关系不存在时拒绝() {
        when(travelOrderMapper.selectByOrderNo("0T01")).thenReturn(payableParent());
        when(orderMapper.selectByOrderNo("0E01")).thenReturn(null);

        DailyTicketRefundResult result = service.requestTravelSubRefund(request());

        assertEquals("9999", result.getRetCode());
        verify(refundMapper, never()).insert(any());
    }

    @Test
    void 子单已有退款单时幂等返成功() {
        when(travelOrderMapper.selectByOrderNo("0T01")).thenReturn(payableParent());
        when(orderMapper.selectByOrderNo("0E01")).thenReturn(child());
        DailyTicketRefund existing = new DailyTicketRefund();
        existing.setRefundOrderNo("RF-OLD");
        existing.setRefundStatus("REFUNDING");
        existing.setRefundAmount(100);
        when(refundMapper.selectByOrderNo("0E01")).thenReturn(existing);

        DailyTicketRefundResult result = service.requestTravelSubRefund(request());

        assertEquals("0000", result.getRetCode());
        assertEquals("PROCESSING", result.getRefundResult());
        assertEquals("RF-OLD", result.getOrderNo());
        verify(refundMapper, never()).insert(any());
    }

    @Test
    void 主单已有整单退款且非失败时拒绝子单退款() {
        when(travelOrderMapper.selectByOrderNo("0T01")).thenReturn(payableParent());
        when(orderMapper.selectByOrderNo("0E01")).thenReturn(child());
        DailyTicketRefund parentRefund = new DailyTicketRefund();
        parentRefund.setRefundStatus("REFUNDING");
        when(refundMapper.selectByOrderNo("0T01")).thenReturn(parentRefund);

        DailyTicketRefundResult result = service.requestTravelSubRefund(request());

        assertEquals("9999", result.getRetCode());
        verify(refundMapper, never()).insert(any());
    }

    @Test
    void 票非激活态时拒绝() {
        when(travelOrderMapper.selectByOrderNo("0T01")).thenReturn(payableParent());
        when(orderMapper.selectByOrderNo("0E01")).thenReturn(child());
        DailyTicketInstance ticket = new DailyTicketInstance();
        ticket.setTicketStatus("USED");
        when(instanceMapper.selectByOrderNo("0E01")).thenReturn(ticket);

        DailyTicketRefundResult result = service.requestTravelSubRefund(request());

        assertEquals("9999", result.getRetCode());
        verify(ticketLockWriter, never()).lockForRefund(any());
    }

    @Test
    void 抢票锁失败时拒绝() {
        stubPayableRelation();
        when(ticketLockWriter.lockForRefund(any())).thenReturn(false);

        DailyTicketRefundResult result = service.requestTravelSubRefund(request());

        assertEquals("9999", result.getRetCode());
        verify(refundMapper, never()).insert(any());
    }

    @Test
    void 小程序渠道只受理不调网关() {
        stubPayableRelation();
        TravelTicketOrder parent = payableParent();
        parent.setOrderSource("6");
        when(travelOrderMapper.selectByOrderNo("0T01")).thenReturn(parent);

        DailyTicketRefundResult result = service.requestTravelSubRefund(request());

        assertEquals("0000", result.getRetCode());
        assertEquals("PROCESSING", result.getRefundResult());
        verify(refundMapper).insert(any());
        verify(refundSettlementService).insertTravelRefundDetails(any(), eq("0T01"), any());
        verify(payGatewayClient, never()).requestRefund(any());
    }

    @Test
    void 网关明确失败时置失败并释放票锁() {
        stubPayableRelation();
        DailyTicketPayGatewayResponse response = new DailyTicketPayGatewayResponse();
        response.setCode(9999);
        response.setMsg("余额不足");
        when(payGatewayClient.requestRefund(any())).thenReturn(response);

        DailyTicketRefundResult result = service.requestTravelSubRefund(request());

        assertEquals("9999", result.getRetCode());
        assertEquals("余额不足", result.getRetMsg());
        verify(refundDetailMapper).updateStatusByRefundOrderNo(anyString(), eq("FAILED"));
        verify(ticketLockWriter).releaseLock("0E01");
        verify(refundSettlementService, never()).markTravelRefunded(any(), any(), any());
    }

    @Test
    void 网关带退款时间时走收口服务() {
        stubPayableRelation();
        DailyTicketPayGatewayResponse response = new DailyTicketPayGatewayResponse();
        response.setCode(0);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("refundTime", "20260921103000");
        response.setData(data);
        when(payGatewayClient.requestRefund(any())).thenReturn(response);

        DailyTicketRefundResult result = service.requestTravelSubRefund(request());

        assertEquals("0000", result.getRetCode());
        assertEquals("SUCCESS", result.getRefundResult());
        verify(refundSettlementService).markTravelRefunded(any(), any(), eq(data));
        verify(ticketLockWriter, never()).releaseLock(anyString());
    }

    @Test
    void 网关未带退款时间时只回填平台退款单号() {
        stubPayableRelation();
        DailyTicketPayGatewayResponse response = new DailyTicketPayGatewayResponse();
        response.setSuccess(Boolean.TRUE);
        response.setData(new LinkedHashMap<>());
        when(payGatewayClient.requestRefund(any())).thenReturn(response);

        DailyTicketRefundResult result = service.requestTravelSubRefund(request());

        assertEquals("0000", result.getRetCode());
        assertEquals("PROCESSING", result.getRefundResult());
        verify(refundSettlementService).updatePlatformRefundNo(any(), any());
        verify(refundSettlementService, never()).markTravelRefunded(any(), any(), any());
    }

    @Test
    void 网关抛异常时留证据并返处理中() {
        stubPayableRelation();
        when(payGatewayClient.requestRefund(any())).thenThrow(new RuntimeException("timeout"));

        DailyTicketRefundResult result = service.requestTravelSubRefund(request());

        assertEquals("0000", result.getRetCode());
        assertEquals("PROCESSING", result.getRefundResult());
        verify(payLogWriter).insert(eq("0E01"), eq("REFUND"), eq("01"), any(), any());
        verify(ticketLockWriter, never()).releaseLock(anyString());
    }

    @Test
    void 子单退款报文商户单号取子单而原支付单号取主单() {
        TravelTicketOrder parent = payableParent();
        DailyTicketRefund refund = new DailyTicketRefund();
        refund.setRefundOrderNo("RF-1");
        refund.setOrderNo("0E01");
        refund.setRefundAmount(100);
        refund.setRefundReason("旅游票子单退款");
        when(payProperties.getRefundNotifyUrl()).thenReturn("");

        Map<String, Object> body = service.buildTravelSubRefundRequest(parent, refund);

        assertEquals("RF-1", body.get("refundOrderNo"));
        assertEquals("0E01", body.get("merchantOrderNo"));
        assertEquals("PAY-1", body.get("orderNo"));
        assertNull(body.get("notifyUrl"));
        assertNotNull(body.get("refundAmount"));
    }
}
