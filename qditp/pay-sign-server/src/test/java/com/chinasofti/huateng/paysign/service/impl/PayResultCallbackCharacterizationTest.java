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

/**
 * {@code receivePayResult}（IF8B 支付结果回调）的特征测试（护栏，2026-09-16）。
 *
 * <p><b>本方法此前零覆盖</b>：逐方法量覆盖时确认，全模块 129 个测试没有一个调用它 ——
 * 而它正是 <b>2026-08-26 生产事故的那条链路</b>（订单 {@code GT20260826210647653586419}
 * 循环重推 8 分钟；同批 4 笔已扣款成功的订单 {@code PAY_STATUS} 长期卡在 {@code PROCESSING}）。
 * 事故之后立的每一条规则都只写在方法体注释里，没有任何自动化断言守着。这个类补上：
 *
 * <ul>
 *   <li><b>定位键 MUST 是 {@code merchantOrderNo}</b>，回调报文的 {@code orderNo} 是**支付中心**
 *       的订单号、只能落 {@code PAY_CENTER_ORDER_NO}。用错键会必然命中 0 行，把「键传错」
 *       和「订单不存在」混成同一种现象 —— 这就是那 4 笔的成因。</li>
 *   <li><b>{@code DEBIT_REQUEST_RESULT} MUST 与 {@code PAY_STATUS} 同步回写</b>：APP 侧读的是
 *       前者，只改后者会让已扣款成功的交易在 APP 上一直显示失败。</li>
 *   <li><b>影响 0 行且本地已是目标状态时 MUST 继续走 {@code syncDebitStatus}</b>，NEVER 提前 return。
 *       重推是收敛 {@code GATE_TXN_PAY} 的唯一机会，在这里 return 非 0000 等于亲手造死循环。</li>
 *   <li><b>影响 0 行且本地不是目标状态时 NEVER 回 0000</b>：那是真没落地，要靠上游重推纠错。</li>
 *   <li><b>达到推送上限则回 0000 止推，但 MUST 同时把回调标成 MANUAL</b>，NEVER 只止推不留痕。</li>
 *   <li><b>远端同步失败 NEVER 抛异常回滚</b>：本地状态与 {@code PAY_CALLBACK_LOG} 是支付中心结果的
 *       唯一凭据，回滚等于丢证据（事故当天循环期间该表零条落库）。</li>
 * </ul>
 *
 * <p>断言一律经 {@code fixture.service} 下钻，理由见 {@link PaySignFacadeFixture} 的门面注释。
 */
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
        // 定位键 NEVER 是支付中心订单号。
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

    /**
     * 重推且本地已是目标状态：跳过回写，但 <b>MUST 继续收敛扣费订单</b>并回 0000。
     *
     * <p>这条是本类最关键的一条：在此处提前 return 会掐掉唯一的重试机会，
     * 且回非 0000 会让支付中心继续推。
     */
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
