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


class RefundResultCallbackCharacterizationTest {

    private static final String MERCHANT_ORDER_NO = "GT20260916100000001586419";
    private static final String REFUND_ORDER_NO = "RF2026092211305514180000104";
    private static final String TXN_DATE = "20260922";
    private static final String NOTIFY_URL =
            "http://127.0.0.1:48000/fep-app/ci/app/paySign/payment/receiveRefundResult";

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

    @Test
    @DisplayName("非终态回调 NEVER 写库：把处理中写进状态列会毁掉 CAS 的前置白名单")
    void nonTerminalCallbackWritesNothing() {
        PaySignFacadeFixture fixture = fixtureWithRefundRow();

        PaySignCallbackResult result = fixture.refundDomainService.receiveRefundResult(callback("PROCESSING"));

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        verify(fixture.payRefundDetailMapper, never()).finishFromQuery(any());
        verify(fixture.payTxnDetailMapper, never()).updateRefundSummary(anyString());
    }

    @Test
    void casMissStillAnswersSuccessAndSkipsSummary() {
        PaySignFacadeFixture fixture = fixtureWithRefundRow();
        when(fixture.payRefundDetailMapper.finishFromQuery(any(PayRefundDetail.class))).thenReturn(0);

        PaySignCallbackResult result = fixture.refundDomainService.receiveRefundResult(callback("SUCCESS"));

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        verify(fixture.payTxnDetailMapper, never()).updateRefundSummary(anyString());
    }

    @Test
    void unknownRefundOrderNoIsRejected() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.payRefundDetailMapper.selectByRefundOrderNo(REFUND_ORDER_NO)).thenReturn(null);

        PaySignCallbackResult result = fixture.refundDomainService.receiveRefundResult(callback("SUCCESS"));

        assertEquals(PaySignErrorCodeEnum.INVALID_PARAM.getCode(), result.getRetCode());
        verify(fixture.payRefundDetailMapper, never()).finishFromQuery(any());
    }

    @Test
    void missingOutRefundNoIsRejectedBeforeLookup() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        ReceiveRefundResultReqDTO request = callback("SUCCESS");
        request.setOutRefundNo(null);

        PaySignCallbackResult result = fixture.refundDomainService.receiveRefundResult(request);

        assertEquals(PaySignErrorCodeEnum.INVALID_PARAM.getCode(), result.getRetCode());
        verify(fixture.payRefundDetailMapper, never()).selectByRefundOrderNo(anyString());
    }

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
