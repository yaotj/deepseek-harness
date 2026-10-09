package com.chinasofti.huateng.paysign.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chinasofti.huateng.model.app.PaySignCallbackResult;
import com.chinasofti.huateng.model.app.ReceiveRefundResultReqDTO;
import com.chinasofti.huateng.model.app.RequestRefundReqDTO;
import com.chinasofti.huateng.paysign.constant.PaySignErrorCodeEnum;
import com.chinasofti.huateng.paysign.entity.PayRefundDetail;
import com.chinasofti.huateng.paysign.entity.PayTxnDetail;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 护栏：支付中心网关 §5.2 退款回调（2026-09-22 新增，P1-3）。
 *
 * <p>三条不可回退的口径：①定位本地行只认 {@code outRefundNo}（我方 {@code REFUND_ORDER_NO}）；
 * ②非终态一律只应答成功、不写库，交 {@code sys_job} 305 收口；
 * ③CAS 命中 0 行仍应答成功（已被回查收口，返错只会引来重推）。</p>
 *
 * <p>外加一条发起侧的：§3.1 的 {@code notifyUrl} 是必填键，配了地址就 MUST 出现在出向 bizData 里 ——
 * 缺它时退款回调**结构上永远到不了**，退款单只能靠回查收敛。</p>
 */
class RefundResultCallbackCharacterizationTest {

    private static final String MERCHANT_ORDER_NO = "GT20260916100000001586419";
    private static final String REFUND_ORDER_NO = "RF2026092211305514180000104";
    private static final String TXN_DATE = "20260922";
    private static final String NOTIFY_URL =
            "http://127.0.0.1:48000/fep-app/ci/app/paySign/payment/receiveRefundResult";

    /** 回调报 SUCCESS：复用 finishFromQuery 这条 CAS 收口，并重算原单汇总。 */
    @Test
    void successCallbackSettlesThroughFinishFromQueryAndSummarizes() {
        PaySignFacadeFixture fixture = fixtureWithRefundRow();
        when(fixture.payRefundDetailMapper.finishFromQuery(any(PayRefundDetail.class))).thenReturn(1);

        PaySignCallbackResult result = fixture.refundDomainService.receiveRefundResult(callback("SUCCESS"));

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        ArgumentCaptor<PayRefundDetail> captor = ArgumentCaptor.forClass(PayRefundDetail.class);
        verify(fixture.payRefundDetailMapper).finishFromQuery(captor.capture());
        assertEquals("SUCCESS", captor.getValue().getRefundStatus());
        assertEquals(TXN_DATE, captor.getValue().getTxnDate());
        verify(fixture.payTxnDetailMapper).updateRefundSummary(MERCHANT_ORDER_NO);
    }

    /** 回调报非终态（处理中）：一行都不写，只应答成功。 */
    @Test
    @DisplayName("非终态回调 NEVER 写库：把处理中写进状态列会毁掉 CAS 的前置白名单")
    void nonTerminalCallbackWritesNothing() {
        PaySignFacadeFixture fixture = fixtureWithRefundRow();

        PaySignCallbackResult result = fixture.refundDomainService.receiveRefundResult(callback("PROCESSING"));

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        verify(fixture.payRefundDetailMapper, never()).finishFromQuery(any());
        verify(fixture.payTxnDetailMapper, never()).updateRefundSummary(anyString());
    }

    /** CAS 命中 0 行（已被退款回查收口）：仍答 0000，且 NEVER 重算汇总。 */
    @Test
    void casMissStillAnswersSuccessAndSkipsSummary() {
        PaySignFacadeFixture fixture = fixtureWithRefundRow();
        when(fixture.payRefundDetailMapper.finishFromQuery(any(PayRefundDetail.class))).thenReturn(0);

        PaySignCallbackResult result = fixture.refundDomainService.receiveRefundResult(callback("SUCCESS"));

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        verify(fixture.payTxnDetailMapper, never()).updateRefundSummary(anyString());
    }

    /** outRefundNo 在本地查不到：拒绝并转人工，NEVER 盲写。 */
    @Test
    void unknownRefundOrderNoIsRejected() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.payRefundDetailMapper.selectByRefundOrderNo(REFUND_ORDER_NO)).thenReturn(null);

        PaySignCallbackResult result = fixture.refundDomainService.receiveRefundResult(callback("SUCCESS"));

        assertEquals(PaySignErrorCodeEnum.INVALID_PARAM.getCode(), result.getRetCode());
        verify(fixture.payRefundDetailMapper, never()).finishFromQuery(any());
    }

    /** 缺 outRefundNo：连查都不查，直接拒。 */
    @Test
    void missingOutRefundNoIsRejectedBeforeLookup() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        ReceiveRefundResultReqDTO request = callback("SUCCESS");
        request.setOutRefundNo(null);

        PaySignCallbackResult result = fixture.refundDomainService.receiveRefundResult(request);

        assertEquals(PaySignErrorCodeEnum.INVALID_PARAM.getCode(), result.getRetCode());
        verify(fixture.payRefundDetailMapper, never()).selectByRefundOrderNo(anyString());
    }

    /** 配了退款回调地址时，§3.1 出向 bizData MUST 带 notifyUrl。 */
    @Test
    void requestRefundSendsConfiguredNotifyUrl() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        fixture.properties.setRequestRefundNotifyUrl(NOTIFY_URL);
        when(fixture.payTxnDetailMapper.selectByOrderNo(MERCHANT_ORDER_NO)).thenReturn(paidOrder());
        fixture.gatewayReturns(PaySignFacadeFixture.gatewaySuccess());

        fixture.refundDomainService.requestRefund(refundRequest());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(fixture.payGatewayClient).request(anyString(), captor.capture());
        assertEquals(NOTIFY_URL, captor.getValue().get("notifyUrl"));
    }

    /** 未配地址时不送该键：退化成今天的「只靠回查收敛」，NEVER 送一个我方没有端点的地址。 */
    @Test
    void requestRefundOmitsNotifyUrlWhenUnconfigured() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.payTxnDetailMapper.selectByOrderNo(MERCHANT_ORDER_NO)).thenReturn(paidOrder());
        fixture.gatewayReturns(PaySignFacadeFixture.gatewaySuccess());

        fixture.refundDomainService.requestRefund(refundRequest());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(fixture.payGatewayClient).request(anyString(), captor.capture());
        assertEquals(false, captor.getValue().containsKey("notifyUrl"));
    }

    private PaySignFacadeFixture fixtureWithRefundRow() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.payRefundDetailMapper.selectByRefundOrderNo(REFUND_ORDER_NO)).thenReturn(refundRow());
        return fixture;
    }

    private PayRefundDetail refundRow() {
        PayRefundDetail row = new PayRefundDetail();
        row.setRefundOrderNo(REFUND_ORDER_NO);
        row.setTxnDate(TXN_DATE);
        row.setOrderNo(MERCHANT_ORDER_NO);
        row.setRefundAmount(100);
        row.setRefundStatus("PROCESSING");
        return row;
    }

    /** §5.2 回调报文：{@code outRefundNo} 才是我方退款流水号。 */
    private ReceiveRefundResultReqDTO callback(String refundResult) {
        ReceiveRefundResultReqDTO request = new ReceiveRefundResultReqDTO();
        request.setOrderNo("286275496309587968");
        request.setOutRefundNo(REFUND_ORDER_NO);
        request.setRefundNo("287000000000000001");
        request.setRefundResult(refundResult);
        request.setRefundResultDesc("退款成功");
        request.setRefundDate("20260922113100");
        request.setRefundAmount(100);
        return request;
    }

    private PayTxnDetail paidOrder() {
        PayTxnDetail payTxn = new PayTxnDetail();
        payTxn.setOrderNo(MERCHANT_ORDER_NO);
        payTxn.setPayCenterOrderNo("286275496309587968");
        payTxn.setPayStatus("SUCCESS");
        payTxn.setTotalAmount(300);
        payTxn.setRefundAmount(0);
        return payTxn;
    }

    private RequestRefundReqDTO refundRequest() {
        RequestRefundReqDTO request = new RequestRefundReqDTO();
        request.setOrderNo(MERCHANT_ORDER_NO);
        request.setRefundAmount(100);
        request.setRefundReason("测试退款");
        return request;
    }
}
