package com.chinasofti.huateng.alipay.paysign.service.impl;

import com.chinasofti.huateng.common.response.AlipayCommonResponse;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripPayNotifyReqDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayRespDTO;
import com.chinasofti.huateng.model.pay.GateTxnPaySyncStatusReqDTO;
import com.chinasofti.huateng.rpc.pay.GateTxnPayClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 钉住支付宝出行扣费回调的收敛口。 */
class PayNotifyDebitStatusSyncTest {

    private static final String ORDER_NO = "GT20260914021300000000001";

    private GateTxnPayClient gateTxnPayClient;
    private PaymentQueryService service;

    @BeforeEach
    void setUp() throws ReflectiveOperationException {
        gateTxnPayClient = mock(GateTxnPayClient.class);
        service = new PaymentQueryService();
        Field field = PaymentQueryService.class.getDeclaredField("gateTxnPayClient");
        field.setAccessible(true);
        field.set(service, gateTxnPayClient);
    }

    @Test
    void transStatusOneIsSyncedAsSuccess() {
        when(gateTxnPayClient.syncDebitStatus(any())).thenReturn(respWith("0000"));

        AlipayCommonResponse response = service.handlePayNotify(notify("1"));

        assertEquals("0000", response.getRetCode(), "远端确认收敛后 MUST 对支付中心返成功");
        assertEquals("SUCCESS", capturedRequest().getPayStatus(), "transStatus=1 MUST 同步成 SUCCESS");
        assertEquals(ORDER_NO, capturedRequest().getOrderNo());
    }

    @Test
    void nonOneTransStatusIsSyncedAsFail() {
        when(gateTxnPayClient.syncDebitStatus(any())).thenReturn(respWith("0000"));

        service.handlePayNotify(notify("2"));

        assertEquals("FAIL", capturedRequest().getPayStatus(), "非 1 的 transStatus MUST 同步成 FAIL");
    }

    @Test
    void nonZeroRetCodeMakesCallbackFailSoPayCenterRepushes() {
        when(gateTxnPayClient.syncDebitStatus(any())).thenReturn(respWith("9999"));

        AlipayCommonResponse response = service.handlePayNotify(notify("1"));

        assertEquals("9999", response.getRetCode(),
                "远端没收敛时 MUST 返非 0000，NEVER 因为「没抛异常」就报成功");
    }

    @Test
    void nullResponseMakesCallbackFail() {
        when(gateTxnPayClient.syncDebitStatus(any())).thenReturn(null);

        AlipayCommonResponse response = service.handlePayNotify(notify("1"));

        assertEquals("9999", response.getRetCode(), "响应为空 MUST 返非 0000");
    }

    @Test
    void blankOrderNoIsRejectedBeforeAnyRpc() {
        AlipayCommonResponse response = service.handlePayNotify(notify("1", " "));

        assertEquals("8001", response.getRetCode(), "订单号为空 MUST 返参数异常");
        verify(gateTxnPayClient, never()).syncDebitStatus(any());
    }

    private GateTxnPaySyncStatusReqDTO capturedRequest() {
        ArgumentCaptor<GateTxnPaySyncStatusReqDTO> captor =
                ArgumentCaptor.forClass(GateTxnPaySyncStatusReqDTO.class);
        verify(gateTxnPayClient).syncDebitStatus(captor.capture());
        return captor.getValue();
    }

    private GateTxnPayRespDTO respWith(String retCode) {
        GateTxnPayRespDTO resp = new GateTxnPayRespDTO();
        resp.setRetCode(retCode);
        return resp;
    }

    private AlipayTripPayNotifyReqDTO notify(String transStatus) {
        return notify(transStatus, ORDER_NO);
    }

    private AlipayTripPayNotifyReqDTO notify(String transStatus, String orderNo) {
        AlipayTripPayNotifyReqDTO request = new AlipayTripPayNotifyReqDTO();
        request.setOrderNo(orderNo);
        request.setTransStatus(transStatus);
        request.setTransAmount("400");
        request.setChannelVoucherId("2026091422001");
        request.setTransTime("2026-09-14 02:13:00");
        return request;
    }
}
