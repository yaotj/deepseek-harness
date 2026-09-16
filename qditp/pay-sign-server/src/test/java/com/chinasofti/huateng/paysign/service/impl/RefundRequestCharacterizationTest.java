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

/**
 * {@code requestRefund}（IF8A 申请退款）的特征测试（护栏，2026-09-16）。
 *
 * <p><b>此前完全零覆盖</b>：全仓测试里对该入口零调用点，而它是本模块唯一「碰钱 + 三条写 +
 * 一次出网」的入口。方法头注释里逐条论证过的三条不变量此前**没有一行代码守着**：
 * <ul>
 *   <li><b>留痕 MUST 先于出网</b>（{@code insert} → {@code markRequesting} → 调支付中心）。
 *       该方法刻意不带 {@code @Transactional}：包成事务后，网关超时会把「留证据」的 INSERT
 *       一起回滚，结果是<b>本地连这一行都不存在</b>而对方可能已受理甚至已退款成功 ——
 *       事后既无从对账也无从补偿。摘掉后最坏停在 {@code REFUND_STATUS='PROCESSING'}，
 *       由 {@code compensateRefundQuery} 回查收口。</li>
 *   <li><b>汇总 MUST 在明细置 SUCCESS 之后</b>：{@code updateRefundSummary} 是按
 *       {@code PAY_REFUND_DETAIL} 全量重算的，顺序颠倒会漏掉本笔。</li>
 *   <li><b>网关失败 MUST 落 RETRY 且 NEVER 重算汇总</b>：这一笔还没成功，算进已退总额
 *       会让后续的可退金额上界偏小、把合法退款拒掉。</li>
 * </ul>
 *
 * <p>断言一律经 {@code fixture.service} 下钻，理由见 {@link PaySignFacadeFixture} 的门面注释。
 */
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
        // 但留痕两步照样发生过：回查补偿要靠这一行找到它。
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

    /**
     * 汇总回写命中 0 行：只打 ERROR，<b>对上游仍答 0000</b>。
     *
     * <p>退款在支付中心侧已经受理，这里回非 0000 会让上游以为没受理而重复申请；
     * 该中间态由 {@code compensateRefundSummary} 的跨表对账兜住。
     */
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
