package com.chinasofti.huateng.paysign.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chinasofti.huateng.model.app.PaySignCallbackResult;
import com.chinasofti.huateng.model.app.ReceivePayResultReqDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayRespDTO;
import com.chinasofti.huateng.model.pay.GateTxnPaySyncStatusReqDTO;
import com.chinasofti.huateng.paysign.constant.PaySignErrorCodeEnum;
import com.chinasofti.huateng.paysign.entity.PayCallbackLog;
import com.chinasofti.huateng.paysign.entity.PayTxnDetail;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PayResultCallbackCharacterizationTest {

    private static final String MERCHANT_ORDER_NO = "GT20260916100000001586419";
    private static final String PAY_CENTER_ORDER_NO = "286275496309587968";
    private static final String CHANNEL_ORDER_NO = "2026091622001400000123456789";
    private static final String RAW_BODY = "{\"orderNo\":\"" + PAY_CENTER_ORDER_NO + "\"}";

    @Test
    void successCallbackWritesBothStatusColumnsAndConvergesGateTxnPay() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.payTxnDetailMapper.updatePayCallback(any(PayTxnDetail.class))).thenReturn(1);
        gateTxnPayReturns(fixture, "0000");

        PaySignCallbackResult result = fixture.service.receivePayResult(payResult("SUCCESS"), RAW_BODY);

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());

        ArgumentCaptor<PayTxnDetail> captor = ArgumentCaptor.forClass(PayTxnDetail.class);
        verify(fixture.payTxnDetailMapper).updatePayCallback(captor.capture());
        PayTxnDetail update = captor.getValue();
        assertEquals(MERCHANT_ORDER_NO, update.getOrderNo());
        assertEquals("SUCCESS", update.getPayStatus());
        assertEquals("SUCCESS", update.getDebitRequestResult());
        assertEquals(PAY_CENTER_ORDER_NO, update.getPayCenterOrderNo());
        assertEquals(CHANNEL_ORDER_NO, update.getChannelOrderNo());
        assertNotEquals(PAY_CENTER_ORDER_NO, update.getOrderNo());

        ArgumentCaptor<GateTxnPaySyncStatusReqDTO> syncCaptor =
                ArgumentCaptor.forClass(GateTxnPaySyncStatusReqDTO.class);
        verify(fixture.gateTxnPayClient).syncDebitStatus(syncCaptor.capture());
        assertEquals(MERCHANT_ORDER_NO, syncCaptor.getValue().getOrderNo());
        assertEquals("SUCCESS", syncCaptor.getValue().getPayStatus());
    }

    @Test
    void callbackAlwaysPersistsEvidenceBeforeProcessing() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.payTxnDetailMapper.updatePayCallback(any(PayTxnDetail.class))).thenReturn(1);
        gateTxnPayReturns(fixture, "0000");

        fixture.service.receivePayResult(payResult("SUCCESS"), RAW_BODY);

        ArgumentCaptor<PayCallbackLog> captor = ArgumentCaptor.forClass(PayCallbackLog.class);
        verify(fixture.payCallbackLogMapper).insert(captor.capture());
        assertEquals(MERCHANT_ORDER_NO, captor.getValue().getMerchantOrderNo());
        assertEquals("PAY", captor.getValue().getCallbackType());
    }

    @Test
    void missingMerchantOrderNoIsRejectedWithoutFallbackKey() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        ReceivePayResultReqDTO request = payResult("SUCCESS");
        request.setMerchantOrderNo(null);

        PaySignCallbackResult result = fixture.service.receivePayResult(request, RAW_BODY);

        assertEquals(PaySignErrorCodeEnum.INVALID_PARAM.getCode(), result.getRetCode());
        verify(fixture.payCallbackLogMapper).insert(any(PayCallbackLog.class));
        verify(fixture.payTxnDetailMapper, never()).updatePayCallback(any());
        verify(fixture.gateTxnPayClient, never()).syncDebitStatus(any());
    }

    @Test
    void repeatedPushWithLocalAlreadyTargetStillConvergesGateTxnPay() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.payTxnDetailMapper.updatePayCallback(any(PayTxnDetail.class))).thenReturn(0);
        when(fixture.payTxnDetailMapper.selectByOrderNo(MERCHANT_ORDER_NO)).thenReturn(existing("SUCCESS"));
        gateTxnPayReturns(fixture, "0000");

        PaySignCallbackResult result = fixture.service.receivePayResult(payResult("SUCCESS"), RAW_BODY);

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        verify(fixture.gateTxnPayClient).syncDebitStatus(any(GateTxnPaySyncStatusReqDTO.class));
    }

    @Test
    void unmatchedOrderReturnsErrorToTriggerRetry() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.payTxnDetailMapper.updatePayCallback(any(PayTxnDetail.class))).thenReturn(0);
        when(fixture.payTxnDetailMapper.selectByOrderNo(MERCHANT_ORDER_NO)).thenReturn(null);
        when(fixture.payCallbackLogMapper.countByMerchantOrderNo(MERCHANT_ORDER_NO, "PAY")).thenReturn(1);

        PaySignCallbackResult result = fixture.service.receivePayResult(payResult("SUCCESS"), RAW_BODY);

        assertEquals(PaySignErrorCodeEnum.SYSTEM_ERROR.getCode(), result.getRetCode());
        verify(fixture.payCallbackLogMapper, never())
                .markManualByMerchantOrderNo(anyString(), anyString(), anyString());
    }

    @Test
    void reachingPushLimitStopsRetryButMarksManual() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.payTxnDetailMapper.updatePayCallback(any(PayTxnDetail.class))).thenReturn(0);
        when(fixture.payTxnDetailMapper.selectByOrderNo(MERCHANT_ORDER_NO)).thenReturn(null);
        when(fixture.payCallbackLogMapper.countByMerchantOrderNo(MERCHANT_ORDER_NO, "PAY")).thenReturn(2);

        PaySignCallbackResult result = fixture.service.receivePayResult(payResult("SUCCESS"), RAW_BODY);

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        verify(fixture.payCallbackLogMapper)
                .markManualByMerchantOrderNo(anyString(), anyString(), anyString());
    }

    @Test
    void gateTxnSyncBusinessFailureReturnsErrorWithoutLosingEvidence() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.payTxnDetailMapper.updatePayCallback(any(PayTxnDetail.class))).thenReturn(1);
        when(fixture.payCallbackLogMapper.countByMerchantOrderNo(MERCHANT_ORDER_NO, "PAY")).thenReturn(1);
        gateTxnPayReturns(fixture, "9999");

        PaySignCallbackResult result = fixture.service.receivePayResult(payResult("SUCCESS"), RAW_BODY);

        assertEquals(PaySignErrorCodeEnum.SYSTEM_ERROR.getCode(), result.getRetCode());
        verify(fixture.payCallbackLogMapper).insert(any(PayCallbackLog.class));
    }

    @Test
    void gateTxnSyncExceptionIsDegradedToRetryNotThrown() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.payTxnDetailMapper.updatePayCallback(any(PayTxnDetail.class))).thenReturn(1);
        when(fixture.payCallbackLogMapper.countByMerchantOrderNo(MERCHANT_ORDER_NO, "PAY")).thenReturn(1);
        when(fixture.gateTxnPayClient.syncDebitStatus(any(GateTxnPaySyncStatusReqDTO.class)))
                .thenThrow(new RuntimeException("connection reset"));

        PaySignCallbackResult result = fixture.service.receivePayResult(payResult("SUCCESS"), RAW_BODY);

        assertEquals(PaySignErrorCodeEnum.SYSTEM_ERROR.getCode(), result.getRetCode());
    }

    @Test
    void failedCallbackWritesFailToBothColumns() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.payTxnDetailMapper.updatePayCallback(any(PayTxnDetail.class))).thenReturn(1);
        gateTxnPayReturns(fixture, "0000");

        fixture.service.receivePayResult(payResult("FAILED"), RAW_BODY);

        ArgumentCaptor<PayTxnDetail> captor = ArgumentCaptor.forClass(PayTxnDetail.class);
        verify(fixture.payTxnDetailMapper).updatePayCallback(captor.capture());
        assertEquals("FAIL", captor.getValue().getPayStatus());
        assertEquals("FAIL", captor.getValue().getDebitRequestResult());
    }

    @Test
    void failedCallbackWithoutPayCenterOrderNoIsAcceptedByMerchantOrderNo() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.payTxnDetailMapper.updatePayCallback(any(PayTxnDetail.class))).thenReturn(1);
        gateTxnPayReturns(fixture, "0000");

        ReceivePayResultReqDTO request = payResult("FAIL");
        request.setOrderNo(null);
        request.setChannelOrderNo(null);
        request.setPayTime(null);

        PaySignCallbackResult result = fixture.service.receivePayResult(request, RAW_BODY);

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());

        ArgumentCaptor<PayTxnDetail> captor = ArgumentCaptor.forClass(PayTxnDetail.class);
        verify(fixture.payTxnDetailMapper).updatePayCallback(captor.capture());
        PayTxnDetail update = captor.getValue();
        assertEquals(MERCHANT_ORDER_NO, update.getOrderNo());
        assertEquals("FAIL", update.getPayStatus());
        assertEquals("FAIL", update.getDebitRequestResult());
        assertNull(update.getPayCenterOrderNo());

        ArgumentCaptor<PayCallbackLog> logCaptor = ArgumentCaptor.forClass(PayCallbackLog.class);
        verify(fixture.payCallbackLogMapper).insert(logCaptor.capture());
        assertEquals(MERCHANT_ORDER_NO, logCaptor.getValue().getOrderNo());
        assertEquals(MERCHANT_ORDER_NO, logCaptor.getValue().getMerchantOrderNo());
    }

    private void gateTxnPayReturns(PaySignFacadeFixture fixture, String retCode) {
        GateTxnPayRespDTO response = new GateTxnPayRespDTO();
        response.setRetCode(retCode);
        when(fixture.gateTxnPayClient.syncDebitStatus(any(GateTxnPaySyncStatusReqDTO.class))).thenReturn(response);
    }

    private ReceivePayResultReqDTO payResult(String status) {
        ReceivePayResultReqDTO request = new ReceivePayResultReqDTO();
        request.setOrderNo(PAY_CENTER_ORDER_NO);
        request.setMerchantOrderNo(MERCHANT_ORDER_NO);
        request.setChannelOrderNo(CHANNEL_ORDER_NO);
        request.setStatus(status);
        request.setPayTime("20260916100500");
        request.setTotalAmount(300);
        request.setPaymentVendor("03");
        return request;
    }

    private PayTxnDetail existing(String payStatus) {
        PayTxnDetail detail = new PayTxnDetail();
        detail.setOrderNo(MERCHANT_ORDER_NO);
        detail.setPayStatus(payStatus);
        return detail;
    }
}
