package com.chinasofti.huateng.paysign.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chinasofti.huateng.model.app.RequestRefundReqDTO;
import com.chinasofti.huateng.model.app.RequestRefundResult;
import com.chinasofti.huateng.paysign.constant.PaySignErrorCodeEnum;
import com.chinasofti.huateng.paysign.entity.PayRefundDetail;
import com.chinasofti.huateng.paysign.entity.PayTxnDetail;
import com.chinasofti.huateng.paysign.model.response.PaySignGatewayResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;


class RefundRequestCharacterizationTest {

    private static final String MERCHANT_ORDER_NO = "GT20260916100000001586419";
    private static final String PAY_CENTER_ORDER_NO = "286275496309587968";

    @Test
    void successPathWritesEvidenceBeforeGoingOutAndSummarizesLast() {
        PaySignFacadeFixture fixture = paidOrderFixture(300, 0);
        fixture.gatewayReturns(PaySignFacadeFixture.gatewaySuccess());

        RequestRefundResult result = fixture.service.requestRefund(refundRequest(100));

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        assertEquals(MERCHANT_ORDER_NO, result.getOrderNo());

        InOrder order = inOrder(fixture.payRefundDetailMapper, fixture.payGatewayClient, fixture.payTxnDetailMapper);
        order.verify(fixture.payRefundDetailMapper).insert(any(PayRefundDetail.class));
        order.verify(fixture.payRefundDetailMapper).markRequesting(anyString(), anyString(), anyString());
        order.verify(fixture.payGatewayClient).request(anyString(), anyMap());
        order.verify(fixture.payRefundDetailMapper).updateRequestResult(any(PayRefundDetail.class));
        order.verify(fixture.payTxnDetailMapper).updateRefundSummary(MERCHANT_ORDER_NO);
    }

    @Test
    void successPathMarksDetailSuccess() {
        PaySignFacadeFixture fixture = paidOrderFixture(300, 0);
        fixture.gatewayReturns(PaySignFacadeFixture.gatewaySuccess());

        fixture.service.requestRefund(refundRequest(100));

        ArgumentCaptor<PayRefundDetail> captor = ArgumentCaptor.forClass(PayRefundDetail.class);
        verify(fixture.payRefundDetailMapper).updateRequestResult(captor.capture());
        assertEquals("SUCCESS", captor.getValue().getRefundStatus());
    }


    @Test
    void gatewayFailureMarksProcessingForRefundQueryAndNeverSummarizes() {
        PaySignFacadeFixture fixture = paidOrderFixture(300, 0);
        fixture.gatewayReturns(PaySignFacadeFixture.gatewayFailure(600, "操作失败"));

        RequestRefundResult result = fixture.service.requestRefund(refundRequest(100));

        assertEquals(PaySignErrorCodeEnum.SYSTEM_ERROR.getCode(), result.getRetCode());
        ArgumentCaptor<PayRefundDetail> captor = ArgumentCaptor.forClass(PayRefundDetail.class);
        verify(fixture.payRefundDetailMapper).updateRequestResult(captor.capture());
        assertEquals("PROCESSING", captor.getValue().getRefundStatus());
        verify(fixture.payTxnDetailMapper, never()).updateRefundSummary(anyString());
        verify(fixture.payRefundDetailMapper).insert(any(PayRefundDetail.class));
        verify(fixture.payRefundDetailMapper).markRequesting(anyString(), anyString(), anyString());
    }

    @Test
    void gatewayFailureSettledBySuccessfulRefundQuery() {
        PaySignFacadeFixture fixture = paidOrderFixture(300, 0);
        fixture.properties.setRefundQueryUrl(PaySignFacadeFixture.GATEWAY_URL_PREFIX + "refund/query");
        when(fixture.payGatewayClient.request(anyString(), anyMap()))
                .thenReturn(PaySignFacadeFixture.gatewayFailure(600, "操作失败"), refundQueried("SUCCESS"));

        RequestRefundResult result = fixture.service.requestRefund(refundRequest(100));

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
        ArgumentCaptor<PayRefundDetail> captor = ArgumentCaptor.forClass(PayRefundDetail.class);
        verify(fixture.payRefundDetailMapper).updateRequestResult(captor.capture());
        assertEquals("SUCCESS", captor.getValue().getRefundStatus());
        verify(fixture.payTxnDetailMapper).updateRefundSummary(MERCHANT_ORDER_NO);
    }

    @Test
    void gatewayFailureSettledByFailedRefundQuery() {
        PaySignFacadeFixture fixture = paidOrderFixture(300, 0);
        fixture.properties.setRefundQueryUrl(PaySignFacadeFixture.GATEWAY_URL_PREFIX + "refund/query");
        when(fixture.payGatewayClient.request(anyString(), anyMap()))
                .thenReturn(PaySignFacadeFixture.gatewayFailure(600, "操作失败"), refundQueried("FAIL"));

        RequestRefundResult result = fixture.service.requestRefund(refundRequest(100));

        assertEquals(PaySignErrorCodeEnum.SYSTEM_ERROR.getCode(), result.getRetCode());
        ArgumentCaptor<PayRefundDetail> captor = ArgumentCaptor.forClass(PayRefundDetail.class);
        verify(fixture.payRefundDetailMapper).updateRequestResult(captor.capture());
        assertEquals("FAIL", captor.getValue().getRefundStatus());
        verify(fixture.payTxnDetailMapper, never()).updateRefundSummary(anyString());
    }

    @Test
    void missingOriginalOrderIsRejectedWithoutWriteOrCall() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.payTxnDetailMapper.selectByOrderNo(MERCHANT_ORDER_NO)).thenReturn(null);

        RequestRefundResult result = fixture.service.requestRefund(refundRequest(100));

        assertEquals(PaySignErrorCodeEnum.INVALID_PARAM.getCode(), result.getRetCode());
        assertEquals("原支付订单不存在", result.getRetMsg());
        verify(fixture.payRefundDetailMapper, never()).insert(any());
        verify(fixture.payGatewayClient, never()).request(anyString(), anyMap());
    }

    @Test
    void overRefundIsRejectedBeforeGoingOut() {
        PaySignFacadeFixture fixture = paidOrderFixture(300, 300);

        RequestRefundResult result = fixture.service.requestRefund(refundRequest(1));

        assertEquals(PaySignErrorCodeEnum.INVALID_PARAM.getCode(), result.getRetCode());
        assertEquals("退款金额超出可退金额", result.getRetMsg());
        verify(fixture.payRefundDetailMapper, never()).insert(any());
        verify(fixture.payGatewayClient, never()).request(anyString(), anyMap());
    }

    @Test
    void nonPositiveRefundAmountIsRejectedBeforeQueryingOriginalOrder() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();

        RequestRefundResult result = fixture.service.requestRefund(refundRequest(0));

        assertEquals(PaySignErrorCodeEnum.INVALID_PARAM.getCode(), result.getRetCode());
        assertEquals("refundAmount必须大于0", result.getRetMsg());
        verify(fixture.payTxnDetailMapper, never()).selectByOrderNo(anyString());
    }

    @Test
    void summaryMissStillAnswersSuccessToUpstream() {
        PaySignFacadeFixture fixture = paidOrderFixture(300, 0);
        fixture.gatewayReturns(PaySignFacadeFixture.gatewaySuccess());
        when(fixture.payTxnDetailMapper.updateRefundSummary(MERCHANT_ORDER_NO)).thenReturn(0);

        RequestRefundResult result = fixture.service.requestRefund(refundRequest(100));

        assertEquals(PaySignErrorCodeEnum.SUCCESS.getCode(), result.getRetCode());
    }

    private PaySignFacadeFixture paidOrderFixture(int totalAmount, int refundedAmount) {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();
        when(fixture.payTxnDetailMapper.selectByOrderNo(MERCHANT_ORDER_NO))
                .thenReturn(paidOrder(totalAmount, refundedAmount));
        return fixture;
    }

    private PayTxnDetail paidOrder(int totalAmount, int refundedAmount) {
        PayTxnDetail payTxn = new PayTxnDetail();
        payTxn.setOrderNo(MERCHANT_ORDER_NO);
        payTxn.setPayCenterOrderNo(PAY_CENTER_ORDER_NO);
        payTxn.setPayStatus("SUCCESS");
        payTxn.setTotalAmount(totalAmount);
        payTxn.setRefundAmount(refundedAmount);
        return payTxn;
    }

    private RequestRefundReqDTO refundRequest(int refundAmount) {
        RequestRefundReqDTO request = new RequestRefundReqDTO();
        request.setOrderNo(MERCHANT_ORDER_NO);
        request.setRefundAmount(refundAmount);
        request.setRefundReason("测试退款");
        return request;
    }

    private PaySignGatewayResponse refundQueried(String status) {
        PaySignGatewayResponse response = PaySignFacadeFixture.gatewaySuccess();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", status);
        data.put("refundOrderNo", "R" + MERCHANT_ORDER_NO);
        response.setData(data);
        return response;
    }
}
