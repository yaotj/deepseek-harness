package com.chinasofti.huateng.paysign.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chinasofti.huateng.model.app.PaySignCallbackResult;
import com.chinasofti.huateng.model.pay.GateTxnPayRespDTO;
import com.chinasofti.huateng.model.pay.GateTxnPaySyncStatusReqDTO;
import com.chinasofti.huateng.model.paysign.CompensateNotifyRespDTO;
import com.chinasofti.huateng.paysign.constant.PaySignErrorCodeEnum;
import com.chinasofti.huateng.paysign.entity.PayCallbackLog;
import com.chinasofti.huateng.paysign.entity.PayTxnDetail;
import com.chinasofti.huateng.paysign.model.response.PaySignGatewayResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PaymentCompensateCharacterizationTest {

    private static final String ORDER_NO = "GT20261119140000000000001";

    private static final String PAY_CENTER_ORDER_NO = "293900000000000001";

    @Test
    void querySuccessConvergesNonTerminalOrderAndSyncsDebitDomain() {
        PaySignFacadeFixture fixture = fixtureWithQueryUrl();
        when(fixture.payTxnDetailMapper.selectCompensablePayStatus(anyString(), anyInt(), anyInt()))
                .thenReturn(List.of(pendingOrder()));
        when(fixture.payCallbackLogMapper.countManualSince(anyString())).thenReturn(0);
        when(fixture.payTxnDetailMapper.updatePayCallback(any(PayTxnDetail.class))).thenReturn(1);
        gateTxnPayReturns(fixture, "0000");
        fixture.gatewayReturns(queryResponse("SUCCESS", "1", "1", "0", "04"));

        CompensateNotifyRespDTO result = fixture.paymentDomainService.compensatePayStatus();

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getResultCode());
        assertEquals(1, result.getScanned());
        assertEquals(1, result.getSubmitted());
        assertEquals(0, result.getSkipped());

        ArgumentCaptor<PayTxnDetail> captor = ArgumentCaptor.forClass(PayTxnDetail.class);
        verify(fixture.payTxnDetailMapper).updatePayCallback(captor.capture());
        PayTxnDetail update = captor.getValue();
        assertEquals(ORDER_NO, update.getOrderNo());
        assertEquals("SUCCESS", update.getPayStatus());
        assertEquals("SUCCESS", update.getDebitRequestResult());
        assertEquals(PAY_CENTER_ORDER_NO, update.getPayCenterOrderNo());

        ArgumentCaptor<GateTxnPaySyncStatusReqDTO> syncCaptor =
                ArgumentCaptor.forClass(GateTxnPaySyncStatusReqDTO.class);
        verify(fixture.gateTxnPayClient).syncDebitStatus(syncCaptor.capture());
        assertEquals(ORDER_NO, syncCaptor.getValue().getOrderNo());
        assertEquals("SUCCESS", syncCaptor.getValue().getPayStatus());
    }

    @Test
    void queryStillProcessingLeavesOrderUntouched() {
        PaySignFacadeFixture fixture = fixtureWithQueryUrl();
        when(fixture.payTxnDetailMapper.selectCompensablePayStatus(anyString(), anyInt(), anyInt()))
                .thenReturn(List.of(pendingOrder()));
        when(fixture.payCallbackLogMapper.countManualSince(anyString())).thenReturn(0);
        fixture.gatewayReturns(queryResponse("PROCESSING", null, null, null, "04"));

        CompensateNotifyRespDTO result = fixture.paymentDomainService.compensatePayStatus();

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getResultCode());
        assertEquals(0, result.getSubmitted());
        assertEquals(1, result.getSkipped());
        verify(fixture.payTxnDetailMapper, never()).updatePayCallback(any());
        verify(fixture.gateTxnPayClient, never()).syncDebitStatus(any());
    }

    @Test
    void queryExplicitFailConvergesToFail() {
        PaySignFacadeFixture fixture = fixtureWithQueryUrl();
        when(fixture.payTxnDetailMapper.selectCompensablePayStatus(anyString(), anyInt(), anyInt()))
                .thenReturn(List.of(pendingOrder()));
        when(fixture.payCallbackLogMapper.countManualSince(anyString())).thenReturn(0);
        when(fixture.payTxnDetailMapper.updatePayCallback(any(PayTxnDetail.class))).thenReturn(1);
        gateTxnPayReturns(fixture, "0000");
        fixture.gatewayReturns(queryResponse("FAIL", "1", null, null, "04"));

        CompensateNotifyRespDTO result = fixture.paymentDomainService.compensatePayStatus();

        assertEquals(1, result.getSubmitted());
        ArgumentCaptor<PayTxnDetail> captor = ArgumentCaptor.forClass(PayTxnDetail.class);
        verify(fixture.payTxnDetailMapper).updatePayCallback(captor.capture());
        assertEquals("FAIL", captor.getValue().getPayStatus());
        assertEquals("FAIL", captor.getValue().getDebitRequestResult());
    }

    @Test
    void replayCallbackRebuildsRequestFromPersistedBody() {
        PaySignFacadeFixture fixture = fixtureWithQueryUrl();
        when(fixture.payCallbackLogMapper.selectLatestByMerchantOrderNo(ORDER_NO, "PAY"))
                .thenReturn(persistedCallback("SUCCESS"));
        when(fixture.payCallbackLogMapper.countByMerchantOrderNo(anyString(), anyString())).thenReturn(1);
        when(fixture.payTxnDetailMapper.updatePayCallback(any(PayTxnDetail.class))).thenReturn(1);
        gateTxnPayReturns(fixture, "0000");

        PaySignCallbackResult result = fixture.paymentDomainService.replayCallback(ORDER_NO);

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        ArgumentCaptor<PayTxnDetail> captor = ArgumentCaptor.forClass(PayTxnDetail.class);
        verify(fixture.payTxnDetailMapper).updatePayCallback(captor.capture());
        assertEquals(ORDER_NO, captor.getValue().getOrderNo());
        assertEquals("SUCCESS", captor.getValue().getPayStatus());
        verify(fixture.payCallbackLogMapper).insert(any(PayCallbackLog.class));
    }

    @Test
    void replayCallbackWithoutEvidenceIsRejected() {
        PaySignFacadeFixture fixture = fixtureWithQueryUrl();
        when(fixture.payCallbackLogMapper.selectLatestByMerchantOrderNo(ORDER_NO, "PAY")).thenReturn(null);

        PaySignCallbackResult result = fixture.paymentDomainService.replayCallback(ORDER_NO);

        assertEquals(PaySignErrorCodeEnum.INVALID_PARAM.getCode(), result.getRetCode());
        assertNotEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        verify(fixture.payTxnDetailMapper, never()).updatePayCallback(any());
    }

    @Test
    void queryRejectedWith9999NotFoundDataIsTreatedAsFail() {
        PaySignFacadeFixture fixture = fixtureWithQueryUrl();
        when(fixture.payTxnDetailMapper.selectCompensablePayStatus(anyString(), anyInt(), anyInt()))
                .thenReturn(List.of(pendingOrder()));
        when(fixture.payCallbackLogMapper.countManualSince(anyString())).thenReturn(0);
        when(fixture.payTxnDetailMapper.updatePayCallback(any(PayTxnDetail.class))).thenReturn(1);
        gateTxnPayReturns(fixture, "0000");
        fixture.gatewayReturns(rejectedResponse(9999, "未找到数据"));

        CompensateNotifyRespDTO result = fixture.paymentDomainService.compensatePayStatus();

        assertEquals(1, result.getSubmitted());
        assertEquals(0, result.getSkipped());
        ArgumentCaptor<PayTxnDetail> captor = ArgumentCaptor.forClass(PayTxnDetail.class);
        verify(fixture.payTxnDetailMapper).updatePayCallback(captor.capture());
        assertEquals("FAIL", captor.getValue().getPayStatus());
    }

    @Test
    void queryRejectedWithBusinessErrorIsLeftForNextRound() {
        PaySignFacadeFixture fixture = fixtureWithQueryUrl();
        when(fixture.payTxnDetailMapper.selectCompensablePayStatus(anyString(), anyInt(), anyInt()))
                .thenReturn(List.of(pendingOrder()));
        when(fixture.payCallbackLogMapper.countManualSince(anyString())).thenReturn(0);
        fixture.gatewayReturns(rejectedResponse(600, "操作失败"));

        CompensateNotifyRespDTO result = fixture.paymentDomainService.compensatePayStatus();

        assertEquals(0, result.getSubmitted());
        assertEquals(1, result.getSkipped());
        verify(fixture.payTxnDetailMapper, never()).updatePayCallback(any());
    }

    private PaySignGatewayResponse rejectedResponse(int code, String msg) {
        PaySignGatewayResponse response = new PaySignGatewayResponse();
        response.setCode(code);
        response.setMsg(msg);
        return response;
    }

    private PaySignFacadeFixture fixtureWithQueryUrl() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        fixture.properties.setPayQueryUrl(PaySignFacadeFixture.GATEWAY_URL_PREFIX + "pay/query");
        return fixture;
    }

    private PayTxnDetail pendingOrder() {
        PayTxnDetail detail = new PayTxnDetail();
        detail.setOrderNo(ORDER_NO);
        detail.setPayStatus("PROCESSING");
        detail.setTxnDate("20261119");
        return detail;
    }

    private PaySignGatewayResponse queryResponse(String status, String totalAmount,
                                                 String cashAmount, String couponAmount, String vendor) {
        PaySignGatewayResponse response = new PaySignGatewayResponse();
        response.setCode(0);
        response.setMsg("成功");
        Map<String, Object> data = new HashMap<>();
        data.put("status", status);
        data.put("orderNo", PAY_CENTER_ORDER_NO);
        data.put("channelOrderNo", "CH20261119140000001");
        data.put("totalAmount", totalAmount);
        data.put("cashAmount", cashAmount);
        data.put("couponAmount", couponAmount);
        data.put("paymentVendor", vendor);
        data.put("payDate", "20261119140000");
        response.setData(data);
        return response;
    }

    private PayCallbackLog persistedCallback(String status) {
        PayCallbackLog log = new PayCallbackLog();
        log.setOrderNo(PAY_CENTER_ORDER_NO);
        log.setMerchantOrderNo(ORDER_NO);
        log.setCallbackType("PAY");
        log.setCallbackStatus(status);
        log.setPaymentVendor("04");
        log.setTotalAmount(1);
        log.setCashAmount(1);
        log.setCouponAmount(0);
        log.setPayTime("20261119140000");
        log.setRawBody("{\"orderNo\":\"" + PAY_CENTER_ORDER_NO + "\",\"merchantOrderNo\":\"" + ORDER_NO
                + "\",\"status\":\"" + status + "\",\"paymentVendor\":\"04\",\"totalAmount\":1,\"cashAmount\":1}");
        return log;
    }

    private void gateTxnPayReturns(PaySignFacadeFixture fixture, String retCode) {
        GateTxnPayRespDTO response = new GateTxnPayRespDTO();
        response.setRetCode(retCode);
        when(fixture.gateTxnPayClient.syncDebitStatus(any(GateTxnPaySyncStatusReqDTO.class))).thenReturn(response);
    }
}
