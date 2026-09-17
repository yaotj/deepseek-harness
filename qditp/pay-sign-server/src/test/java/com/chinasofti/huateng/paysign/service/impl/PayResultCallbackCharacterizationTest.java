package com.chinasofti.huateng.paysign.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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

/** 护栏：支付回调按 merchantOrderNo 定位、两列状态同写、影响 0 行仍继续同步扣费订单。 */
class PayResultCallbackCharacterizationTest {

    /** 我方商户订单号：{@code PAY_TXN_DETAIL.ORDER_NO} 存的是这个。 */
    private static final String MERCHANT_ORDER_NO = "GT20260916100000001586419";
    /** 支付中心侧订单号：回调报文的 {@code orderNo}，只能落 {@code PAY_CENTER_ORDER_NO}。 */
    private static final String PAY_CENTER_ORDER_NO = "286275496309587968";
    private static final String CHANNEL_ORDER_NO = "2026091622001400000123456789";
    private static final String RAW_BODY = "{\"orderNo\":\"" + PAY_CENTER_ORDER_NO + "\"}";

    /** 成功回调：两列状态同写、定位键用商户订单号、支付中心订单号落到独立列，并收敛扣费订单。 */
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

    /** 回调报文一律先落 PAY_CALLBACK_LOG 留证据，再谈处理结果。 */
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

    /** 缺 merchantOrderNo：证据照落，但 NEVER 拿支付中心 orderNo 兜底去 UPDATE，且 NEVER 回 0000。 */
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

    /** 重推且本地已是目标状态：跳过回写，但 MUST 继续收敛扣费订单并回 0000。 */
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

    /** 影响 0 行且本地不是目标状态（真没落地）：回非 0000 让支付中心重推，NEVER 静默吞。 */
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

    /** 达到推送上限（含本次共 2 次）：止推回 0000，但 MUST 同时把回调标成 MANUAL。 */
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

    /** 远端同步业务失败（retCode 非 0000）：回非 0000 等重推，NEVER 抛异常、证据照留。 */
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

    /** 远端抛异常同样只降级成「等重推」，NEVER 让异常穿出去。 */
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

    /** 失败回调：PAY_STATUS 落 FAIL，DEBIT_REQUEST_RESULT 同步落 FAIL。 */
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
