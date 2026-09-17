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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

/** 护栏：退款留痕先于出网、汇总在明细置 SUCCESS 之后、网关失败落 RETRY 且不重算汇总。 */
class RefundRequestCharacterizationTest {

    private static final String MERCHANT_ORDER_NO = "GT20260916100000001586419";
    private static final String PAY_CENTER_ORDER_NO = "286275496309587968";

    /** 成功路径的完整顺序：留痕两步 → 出网 → 回写明细 → 最后才重算汇总。 */
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

    /** 成功回写的明细状态是 SUCCESS，且带上网关返回的号码。 */
    @Test
    void successPathMarksDetailSuccess() {
        PaySignFacadeFixture fixture = paidOrderFixture(300, 0);
        fixture.gatewayReturns(PaySignFacadeFixture.gatewaySuccess());

        fixture.service.requestRefund(refundRequest(100));

        ArgumentCaptor<PayRefundDetail> captor = ArgumentCaptor.forClass(PayRefundDetail.class);
        verify(fixture.payRefundDetailMapper).updateRequestResult(captor.capture());
        assertEquals("SUCCESS", captor.getValue().getRefundStatus());
    }

    /** 网关失败：明细落 RETRY，且 NEVER 重算汇总（这一笔还没成功）。 */
    @Test
    void gatewayFailureMarksRetryAndNeverSummarizes() {
        PaySignFacadeFixture fixture = paidOrderFixture(300, 0);
        fixture.gatewayReturns(PaySignFacadeFixture.gatewayFailure(600, "操作失败"));

        RequestRefundResult result = fixture.service.requestRefund(refundRequest(100));

        assertEquals(PaySignErrorCodeEnum.SYSTEM_ERROR.getCode(), result.getRetCode());
        ArgumentCaptor<PayRefundDetail> captor = ArgumentCaptor.forClass(PayRefundDetail.class);
        verify(fixture.payRefundDetailMapper).updateRequestResult(captor.capture());
        assertEquals("RETRY", captor.getValue().getRefundStatus());
        verify(fixture.payTxnDetailMapper, never()).updateRefundSummary(anyString());
        verify(fixture.payRefundDetailMapper).insert(any(PayRefundDetail.class));
        verify(fixture.payRefundDetailMapper).markRequesting(anyString(), anyString(), anyString());
    }

    /** 原支付订单不存在：本地就拒，NEVER 落明细、NEVER 出网。 */
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

    /** 超出可退金额：同样在出网之前拒掉。 */
    @Test
    void overRefundIsRejectedBeforeGoingOut() {
        PaySignFacadeFixture fixture = paidOrderFixture(300, 300);

        RequestRefundResult result = fixture.service.requestRefund(refundRequest(1));

        assertEquals(PaySignErrorCodeEnum.INVALID_PARAM.getCode(), result.getRetCode());
        assertEquals("退款金额超出可退金额", result.getRetMsg());
        verify(fixture.payRefundDetailMapper, never()).insert(any());
        verify(fixture.payGatewayClient, never()).request(anyString(), anyMap());
    }

    /** 报文校验：refundAmount 必须大于 0，且在查原单之前就拦住。 */
    @Test
    void nonPositiveRefundAmountIsRejectedBeforeQueryingOriginalOrder() {
        PaySignFacadeFixture fixture = PaySignFacadeFixture.create();

        RequestRefundResult result = fixture.service.requestRefund(refundRequest(0));

        assertEquals(PaySignErrorCodeEnum.INVALID_PARAM.getCode(), result.getRetCode());
        assertEquals("refundAmount必须大于0", result.getRetMsg());
        verify(fixture.payTxnDetailMapper, never()).selectByOrderNo(anyString());
    }

    /** 汇总回写命中 0 行：只打 ERROR，对上游仍答 0000。 */
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
}
