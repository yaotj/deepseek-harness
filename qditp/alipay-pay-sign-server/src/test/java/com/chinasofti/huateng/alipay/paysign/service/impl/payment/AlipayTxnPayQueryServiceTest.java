package com.chinasofti.huateng.alipay.paysign.service.impl.payment;

import com.chinasofti.huateng.alipay.paysign.entity.AlipayPayTxnDetail;
import com.chinasofti.huateng.alipay.paysign.exception.BusinessException;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipayPayTxnDetailMapper;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripPayQueryReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripPayQueryRespDTO;
import com.chinasofti.huateng.alipay.paysign.port.PayCenterPort;
import com.chinasofti.huateng.alipay.paysign.port.PayCenterReply;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 钉住 {@link AlipayTxnPayQueryService} 的回写口径。
 *
 * <p>核心断言只有一条：<b>什么情况下允许动 {@code PAY_STATUS}</b>。
 * 只有「查询成立（{@code retCode == SUCCESS}）且交易状态可判定」才回写，其余五种形态
 * （网关业务拒绝 / 交易状态是中间态 / 交易状态缺失 / {@code Rejected} / {@code NoAnswer}）
 * <b>一律不回写</b> —— 把它们当失败会让单子被误判成扣款失败、既不回查也不补偿，是资损路径。
 * <b>NEVER 为了「让状态尽快收敛」把其中任何一支改成落 FAIL。</b>
 */
class AlipayTxnPayQueryServiceTest {

    private static final String ORDER_NO = "GT20260918021300000000001";

    private AlipayPayTxnDetailMapper mapper;
    private PayCenterPort payCenterPort;
    private AlipayTxnPayQueryService service;

    @BeforeEach
    void setUp() {
        mapper = mock(AlipayPayTxnDetailMapper.class);
        payCenterPort = mock(PayCenterPort.class);
        service = new AlipayTxnPayQueryService(mapper, payCenterPort);
        when(mapper.selectByOrderNo(ORDER_NO)).thenReturn(detail("PROCESSING"));
    }

    @Test
    void successTradeStatusIsWrittenBack() {
        when(payCenterPort.payQuery(any())).thenReturn(accepted("SUCCESS", Map.of(
                "status", "SUCCESS", "channelOrderNo", "2026091822001", "payDate", "20260918021500")));
        when(mapper.updatePayQueryResultIfNotSuccess(any())).thenReturn(1);

        AlipayTripPayQueryRespDTO response = service.payQuery(request());

        AlipayPayTxnDetail written = capturedUpdate();
        assertEquals("SUCCESS", written.getPayStatus(), "交易状态 SUCCESS MUST 回写成 SUCCESS");
        assertEquals("2026091822001", written.getChannelOrderNo(), "渠道订单号 MUST 一并回写");
        assertEquals("0000", response.getRetCode());
        assertEquals("20260918021500", response.getPaymentTime(), "支付时间取应答，本表没有这一列");
    }

    @Test
    void failedTradeStatusIsWrittenBack() {
        when(payCenterPort.payQuery(any())).thenReturn(accepted("SUCCESS", Map.of("status", "FAILED")));
        when(mapper.updatePayQueryResultIfNotSuccess(any())).thenReturn(1);

        service.payQuery(request());

        assertEquals("FAIL", capturedUpdate().getPayStatus(), "交易状态 FAILED MUST 归一成 FAIL");
    }

    @Test
    void intermediateTradeStatusIsNeverWrittenBack() {
        when(payCenterPort.payQuery(any())).thenReturn(accepted("SUCCESS", Map.of("status", "PROCESSING")));

        service.payQuery(request());

        verify(mapper, never()).updatePayQueryResultIfNotSuccess(any());
    }

    @Test
    void missingTradeStatusIsNeverWrittenBack() {
        when(payCenterPort.payQuery(any())).thenReturn(accepted("SUCCESS", Map.of("channelOrderNo", "2026091822001")));

        service.payQuery(request());

        verify(mapper, never()).updatePayQueryResultIfNotSuccess(any());
    }

    @Test
    void bizRejectedQueryIsNeverWrittenBackAsFail() {
        when(payCenterPort.payQuery(any())).thenReturn(accepted("9999", Map.of()));

        service.payQuery(request());

        verify(mapper, never()).updatePayQueryResultIfNotSuccess(any());
    }

    @Test
    void rejectedReplyIsNeverWrittenBack() {
        when(payCenterPort.payQuery(any())).thenReturn(new PayCenterReply.Rejected(500, Boolean.FALSE, "网关异常", "{}"));

        service.payQuery(request());

        verify(mapper, never()).updatePayQueryResultIfNotSuccess(any());
    }

    @Test
    void noAnswerIsNeverWrittenBack() {
        when(payCenterPort.payQuery(any())).thenReturn(new PayCenterReply.NoAnswer());

        service.payQuery(request());

        verify(mapper, never()).updatePayQueryResultIfNotSuccess(any());
    }

    @Test
    void outboundExceptionIsNeverWrittenBack() {
        when(payCenterPort.payQuery(any())).thenThrow(new IllegalStateException("连接超时"));

        AlipayTripPayQueryRespDTO response = service.payQuery(request());

        verify(mapper, never()).updatePayQueryResultIfNotSuccess(any());
        assertEquals("PROCESSING", response.getTradeStatus(), "出网异常时本地状态 MUST 原样返回");
    }

    @Test
    void missingDetailIsRejectedBeforeOutbound() {
        when(mapper.selectByOrderNo(ORDER_NO)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.payQuery(request()));
        verify(payCenterPort, never()).payQuery(any());
    }

    @Test
    void blankOrderNoIsRejectedBeforeAnyQuery() {
        AlipayTripPayQueryReqDTO request = new AlipayTripPayQueryReqDTO();
        request.setOrderNo(" ");

        assertThrows(BusinessException.class, () -> service.payQuery(request));
        verify(mapper, never()).selectByOrderNo(anyString());
        verify(payCenterPort, never()).payQuery(any());
    }

    @Test
    void channelAgreementNoFallsBackToDetailColumn() {
        when(payCenterPort.payQuery(any())).thenReturn(accepted("SUCCESS", Map.of("status", "SUCCESS")));
        when(mapper.updatePayQueryResultIfNotSuccess(any())).thenReturn(1);

        service.payQuery(request());

        assertEquals("20260918000001", capturedBizData().get("channelAgreementNo"),
                "请求没带渠道协议号时 MUST 取本表列，NEVER 再去解 ALIPAY_PAY_LOG 的 JSON");
        assertEquals("0007", capturedBizData().get("cardIssueCode"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> capturedBizData() {
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(payCenterPort).payQuery(captor.capture());
        return captor.getValue();
    }

    private AlipayPayTxnDetail capturedUpdate() {
        ArgumentCaptor<AlipayPayTxnDetail> captor = ArgumentCaptor.forClass(AlipayPayTxnDetail.class);
        verify(mapper).updatePayQueryResultIfNotSuccess(captor.capture());
        return captor.getValue();
    }

    private PayCenterReply accepted(String retCode, Map<String, Object> data) {
        return new PayCenterReply.Accepted(200, Boolean.TRUE, "ok", "{}", retCode, "处理成功", new HashMap<>(data));
    }

    private AlipayTripPayQueryReqDTO request() {
        AlipayTripPayQueryReqDTO request = new AlipayTripPayQueryReqDTO();
        request.setOrderNo(ORDER_NO);
        return request;
    }

    private AlipayPayTxnDetail detail(String payStatus) {
        AlipayPayTxnDetail detail = new AlipayPayTxnDetail();
        detail.setOrderNo(ORDER_NO);
        detail.setTxnDate("20260918");
        detail.setPayStatus(payStatus);
        detail.setAmount(400);
        detail.setChannelAgreementNo("20260918000001");
        return detail;
    }
}
