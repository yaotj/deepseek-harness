package com.chinasofti.huateng.alipay.paysign.service.impl.refund;

import com.chinasofti.huateng.alipay.paysign.entity.AlipayPayLog;
import com.chinasofti.huateng.alipay.paysign.config.PayCenterProperties;
import com.chinasofti.huateng.alipay.paysign.exception.BusinessException;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipayPayLogMapper;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipaySignInfoMapper;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripRequestRefundReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripRequestRefundRespDTO;
import com.chinasofti.huateng.alipay.paysign.port.PayCenterPort;
import com.chinasofti.huateng.alipay.paysign.port.PayCenterReply;
import com.chinasofti.huateng.alipay.paysign.service.impl.support.RefundAmountCalculator;
import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 钉住 {@link AlipayPayRefundServiceImpl} 的四条口径。
 *
 * <p>①<b>存在未收口（PROCESSING）的退款明细时 MUST 在落库与出网之前就拒绝</b> —— 放行第二笔等于对同一笔
 * 原支付退两次款，而金额校验读的是汇总列、拦不住未收口那笔。
 *
 * <p>②<b>原支付非 SUCCESS 一律拒绝</b>，且拒绝发生在幂等计数与落库之前。
 *
 * <p>③<b>退款成功 MUST 先把明细置 SUCCESS、再刷汇总</b>：汇总 SQL 是按 {@code ALIPAY_REFUND_LOG} 重算的，
 * 顺序反了算出来的是旧值。业务拒绝那一支落 FAIL 且 <b>NEVER 刷汇总</b>（钱没退出去）。
 *
 * <p>④<b>{@code Rejected} / {@code NoAnswer} 都保持 PROCESSING、NEVER 置 FAIL、NEVER 刷汇总</b> ——
 * 钱可能已经退了，落 FAIL 会让这笔被当成没退成、随后被人再退一次。
 */
class AlipayPayRefundServiceImplTest {

    private static final String ORDER_NO = "GT20260918021300000000001";
    private static final String CARD_ID = "0007000000000001";
    private static final String CHANNEL_AGREEMENT_NO = "20260918990001";
    private static final String REFUND_SEQ = "9f1c2b7a4d5e4f0a8b3c6d7e8f901234";
    private static final String REFUND_NOTIFY_URL = "http://58.56.166.170:48000/fep-alipay/notify/payment/refundNotify";

    private AlipayPayLogMapper alipayPayLogMapper;
    private AlipaySignInfoMapper alipaySignInfoMapper;
    private RefundLogRepository refundLogRepository;
    private PayCenterPort payCenterPort;
    private AlipayPayRefundServiceImpl service;

    @BeforeEach
    void setUp() {
        alipayPayLogMapper = mock(AlipayPayLogMapper.class);
        alipaySignInfoMapper = mock(AlipaySignInfoMapper.class);
        refundLogRepository = mock(RefundLogRepository.class);
        payCenterPort = mock(PayCenterPort.class);
        PayCenterProperties payCenterProperties = new PayCenterProperties();
        payCenterProperties.setRefundNotifyUrl(REFUND_NOTIFY_URL);
        service = new AlipayPayRefundServiceImpl(alipaySignInfoMapper,
                new RefundAmountCalculator(), refundLogRepository, payCenterPort, payCenterProperties);

        when(refundLogRepository.findPayLog(ORDER_NO)).thenReturn(payLog("SUCCESS"));
        when(alipaySignInfoMapper.selectByCardIdAndChannel(CARD_ID, "ALIPAY")).thenReturn(signInfo());
        when(refundLogRepository.countProcessing(ORDER_NO)).thenReturn(0);
        when(refundLogRepository.openRefund(any(), any(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(REFUND_SEQ);
    }

    @Test
    void processingRefundIsRejectedBeforePersistenceAndOutbound() {
        when(refundLogRepository.countProcessing(ORDER_NO)).thenReturn(1);

        BusinessException error = assertThrows(BusinessException.class, () -> service.requestRefund(request(null)));

        assertEquals("该订单存在处理中的退款，请先确认上一笔结果", error.getMessage());
        verify(refundLogRepository, never()).openRefund(any(), any(), anyString(), anyString(), anyString(), anyString());
        verify(payCenterPort, never()).requestRefund(any());
    }

    @Test
    void unsettledPayLogIsRejectedBeforeCountingRefunds() {
        when(refundLogRepository.findPayLog(ORDER_NO)).thenReturn(payLog("PROCESSING"));

        BusinessException error = assertThrows(BusinessException.class, () -> service.requestRefund(request(null)));

        assertEquals("原支付订单未支付成功", error.getMessage());
        verify(refundLogRepository, never()).countProcessing(anyString());
        verify(payCenterPort, never()).requestRefund(any());
    }

    @Test
    void missingPayLogIsRejected() {
        when(refundLogRepository.findPayLog(ORDER_NO)).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class, () -> service.requestRefund(request(null)));

        assertEquals("原支付记录不存在", error.getMessage());
        verify(payCenterPort, never()).requestRefund(any());
    }

    @Test
    void blankOrderNoIsRejectedBeforeQueryingPayLog() {
        AlipayTripRequestRefundReqDTO request = request(null);
        request.setOrderNo(null);

        BusinessException error = assertThrows(BusinessException.class, () -> service.requestRefund(request));

        assertEquals("订单号不能为空", error.getMessage());
        verify(alipayPayLogMapper, never()).selectByOrderNo(anyString());
    }

    @Test
    void missingSignInfoIsRejectedBeforePersistence() {
        when(alipaySignInfoMapper.selectByCardIdAndChannel(CARD_ID, "ALIPAY")).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.requestRefund(request(null)));

        verify(refundLogRepository, never()).openRefund(any(), any(), anyString(), anyString(), anyString(), anyString());
        verify(payCenterPort, never()).requestRefund(any());
    }

    @Test
    void refundSuccessWritesDetailBeforeRefreshingSummary() {
        when(payCenterPort.requestRefund(any())).thenReturn(accepted("SUCCESS", "退款成功"));

        AlipayTripRequestRefundRespDTO response = service.requestRefund(request(null));

        InOrder order = inOrder(refundLogRepository);
        order.verify(refundLogRepository).writeResult(REFUND_SEQ, "SUCCESS", "0000", "退款成功", "{}");
        order.verify(refundLogRepository).refreshSummary(eq(ORDER_NO), anyString());
        assertEquals("0000", response.getRetCode());
        assertEquals("退款成功", response.getRetMsg());
    }

    @Test
    void fullRefundAmountFallsBackToRemainingPaidAmount() {
        when(payCenterPort.requestRefund(any())).thenReturn(accepted("SUCCESS", "退款成功"));

        service.requestRefund(request(null));

        verify(refundLogRepository).openRefund(any(), any(), eq(CHANNEL_AGREEMENT_NO), eq("300"),
                anyString(), anyString());
    }

    @Test
    void bizRejectedIsTerminalFailAndNeverRefreshesSummary() {
        when(payCenterPort.requestRefund(any())).thenReturn(accepted("9999", "原订单不可退"));

        AlipayTripRequestRefundRespDTO response = service.requestRefund(request(null));

        verify(refundLogRepository).writeResult(REFUND_SEQ, "FAIL", "9999", "原订单不可退", "{}");
        verify(refundLogRepository, never()).refreshSummary(anyString(), anyString());
        assertEquals("9999", response.getRetCode());
    }

    @Test
    void rejectedReplyKeepsProcessingAndNeverRefreshesSummary() {
        when(payCenterPort.requestRefund(any()))
                .thenReturn(new PayCenterReply.Rejected(600, null, "操作失败", "{\"code\":600}"));

        AlipayTripRequestRefundRespDTO response = service.requestRefund(request(null));

        verify(refundLogRepository).writeResult(REFUND_SEQ, "PROCESSING", "INIT",
                "退款结果未知，待人工核对", "{\"code\":600}");
        verify(refundLogRepository, never()).refreshSummary(anyString(), anyString());
        assertEquals("退款结果未知，请稍后核对", response.getRetMsg());
    }

    @Test
    void noAnswerKeepsProcessingAndNeverRefreshesSummary() {
        when(payCenterPort.requestRefund(any())).thenReturn(new PayCenterReply.NoAnswer());

        AlipayTripRequestRefundRespDTO response = service.requestRefund(request(null));

        verify(refundLogRepository).writeResult(REFUND_SEQ, "PROCESSING", "INIT",
                "退款结果未知，待人工核对", null);
        verify(refundLogRepository, never()).refreshSummary(anyString(), anyString());
        assertEquals("退款结果未知，请稍后核对", response.getRetMsg());
    }

    @Test
    void refundAmountOverRemainingIsRejectedBeforePersistence() {
        assertThrows(IllegalArgumentException.class, () -> service.requestRefund(request("400")));

        verify(refundLogRepository, never()).openRefund(any(), any(), anyString(), anyString(), anyString(), anyString());
        verify(payCenterPort, never()).requestRefund(any());
    }

    /**
     * 出网 bizData MUST 带上 {@code notifyUrl}（契约 §3.1 必填）——
     * 不送这个键时支付中心无处回推退款结果，`/api/payment/refundNotify` 永远收不到回调。
     */
    @Test
    void outboundBizDataCarriesRefundNotifyUrl() {
        when(payCenterPort.requestRefund(any())).thenReturn(accepted("SUCCESS", "退款成功"));

        service.requestRefund(request(null));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(payCenterPort).requestRefund(captor.capture());
        assertEquals(REFUND_NOTIFY_URL, captor.getValue().get("notifyUrl"));
    }

    private PayCenterReply accepted(String retCode, String retMsg) {
        return new PayCenterReply.Accepted(200, Boolean.TRUE, "ok", "{}", retCode, retMsg, new HashMap<>(Map.of()));
    }

    private AlipayTripRequestRefundReqDTO request(String refundAmount) {
        AlipayTripRequestRefundReqDTO request = new AlipayTripRequestRefundReqDTO();
        request.setOrderNo(ORDER_NO);
        request.setRefundAmount(refundAmount);
        return request;
    }

    /** 原支付 400 分、已退 100 分，因此可退余额是 300 分。 */
    private AlipayPayLog payLog(String payStatus) {
        AlipayPayLog payLog = new AlipayPayLog();
        payLog.setOrderNo(ORDER_NO);
        payLog.setPayStatus(payStatus);
        payLog.setCardId(CARD_ID);
        payLog.setThirdUserId("2088100000000001");
        payLog.setPayAmount("400");
        payLog.setRefundAmount("100");
        return payLog;
    }

    private AlipaySignInfo signInfo() {
        AlipaySignInfo signInfo = new AlipaySignInfo();
        signInfo.setCardId(CARD_ID);
        signInfo.setChannelAgreementCode(CHANNEL_AGREEMENT_NO);
        return signInfo;
    }
}
