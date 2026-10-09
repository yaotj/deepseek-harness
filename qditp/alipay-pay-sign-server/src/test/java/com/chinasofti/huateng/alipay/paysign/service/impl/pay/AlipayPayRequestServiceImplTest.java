package com.chinasofti.huateng.alipay.paysign.service.impl.pay;

import com.chinasofti.huateng.alipay.paysign.config.PayCenterProperties;
import com.chinasofti.huateng.alipay.paysign.entity.AlipayPayTxnDetail;
import com.chinasofti.huateng.alipay.paysign.exception.BusinessException;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipaySignInfoMapper;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripRequestPayReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripRequestPayRespDTO;
import com.chinasofti.huateng.alipay.paysign.port.BlacklistPort;
import com.chinasofti.huateng.alipay.paysign.port.PayCenterPort;
import com.chinasofti.huateng.alipay.paysign.port.PayCenterReply;
import com.chinasofti.huateng.alipay.paysign.service.impl.support.AlipayPayCenterMsgLogWriter;
import com.chinasofti.huateng.alipay.paysign.service.impl.support.BizDataBuilder;
import com.chinasofti.huateng.alipay.paysign.service.impl.support.IndustryDetailEnricher;
import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfo;
import com.chinasofti.huateng.model.pay.GateTxnPayListDTO;
import com.chinasofti.huateng.rpc.outcome.RpcOutcome;
import com.chinasofti.huateng.rpc.pay.GateTxnPayClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 钉住 {@link AlipayPayRequestServiceImpl} 的四条口径。
 *
 * <p>①<b>已 SUCCESS 的单子重推时，MUST 在取 {@code txnDate} 的 RPC 之前就短路</b> ——
 * 这是本实现与旧 {@code AlipayTxnPayService} 的唯一行为差异，也是本类最重要的一条：
 * 顺序反了会让一笔已成功的扣款在 gate-txn-pay 不可达时被报成失败。
 *
 * <p>②<b>受理成功只能写 PROCESSING</b>，NEVER 写 SUCCESS（那是拿受理冒充扣款成功）。
 *
 * <p>③<b>只有「拿到业务拒绝 + payQuery 回查确认 FAIL」才加黑名单</b>：{@code Rejected} /
 * {@code NoAnswer} 都不加；业务拒绝那一支若回查是 SUCCESS（幂等拒答）或压根确认不了，也不加。
 *
 * <p>④<b>{@code NoAnswer} 与出网抛异常一个字都不回写</b>，状态留 {@code PROCESSING} 等回查 ——
 * 钱可能已经扣了，落 FAIL 是资损路径。
 */
class AlipayPayRequestServiceImplTest {

    private static final String ORDER_NO = "GT20260918021300000000001";
    private static final String THIRD_USER_ID = "2088100000000001";

    private AlipaySignInfoMapper alipaySignInfoMapper;
    private PayTxnRepository payTxnRepository;
    private AlipayPayCenterMsgLogWriter msgLogWriter;
    private GateTxnPayClient gateTxnPayClient;
    private PayCenterPort payCenterPort;
    private BlacklistPort blacklistPort;
    private AlipayPayRequestServiceImpl service;

    @BeforeEach
    void setUp() {
        alipaySignInfoMapper = mock(AlipaySignInfoMapper.class);
        payTxnRepository = mock(PayTxnRepository.class);
        msgLogWriter = mock(AlipayPayCenterMsgLogWriter.class);
        gateTxnPayClient = mock(GateTxnPayClient.class);
        payCenterPort = mock(PayCenterPort.class);
        blacklistPort = mock(BlacklistPort.class);
        service = new AlipayPayRequestServiceImpl(alipaySignInfoMapper, payTxnRepository, msgLogWriter,
                gateTxnPayClient, mock(IndustryDetailEnricher.class), mock(BizDataBuilder.class),
                new PayCenterProperties(), payCenterPort, blacklistPort);

        when(alipaySignInfoMapper.selectByThirdUserIdAndChannel(THIRD_USER_ID, "ALIPAY")).thenReturn(signInfo());
        when(gateTxnPayClient.queryByOrderNo(ORDER_NO)).thenReturn(order("20260918"));
        when(blacklistPort.addBlackList(anyString(), anyString(), any(), anyString())).thenReturn(new RpcOutcome.Ok());
    }

    @Test
    void settledOrderShortCircuitsBeforeTxnDateLookup() {
        when(payTxnRepository.findByOrderNo(ORDER_NO)).thenReturn(detail("SUCCESS"));

        AlipayTripRequestPayRespDTO response = service.requestPay(request());

        assertEquals("0000", response.getRetCode());
        assertEquals("支付成功", response.getRetMsg());
        verify(gateTxnPayClient, never()).queryByOrderNo(anyString());
        verify(payTxnRepository, never()).openAttempt(any(), any(), anyString(), any());
        verify(payCenterPort, never()).requestPay(any());
    }

    @Test
    void failedOrderIsRetriedInsteadOfShortCircuited() {
        when(payTxnRepository.findByOrderNo(ORDER_NO)).thenReturn(detail("FAIL"));
        when(payCenterPort.requestPay(any())).thenReturn(accepted("SUCCESS", Map.of()));

        service.requestPay(request());

        verify(payCenterPort).requestPay(any());
    }

    @Test
    void acceptedSuccessIsWrittenAsProcessingNotSuccess() {
        when(payCenterPort.requestPay(any())).thenReturn(accepted("SUCCESS", Map.of(
                "payCenterOrderNo", "PC202609180001", "channelOrderNo", "2026091822001")));

        AlipayTripRequestPayRespDTO response = service.requestPay(request());

        verify(payTxnRepository).writeRequestResult(ORDER_NO, "PROCESSING", "PC202609180001", "2026091822001");
        assertEquals("0000", response.getRetCode());
        verify(blacklistPort, never()).addBlackList(anyString(), anyString(), any(), anyString());
    }

    @Test
    void bizRejectedIsTerminalFailAndBlacklisted() {
        when(payCenterPort.requestPay(any())).thenReturn(accepted("9999", Map.of("channelOrderNo", "2026091822001")));
        when(payCenterPort.payQuery(any())).thenReturn(accepted("SUCCESS", Map.of("tradeStatus", "FAIL")));

        AlipayTripRequestPayRespDTO response = service.requestPay(request());

        verify(payTxnRepository).writeRequestResult(ORDER_NO, "FAIL", null, "2026091822001");
        verify(blacklistPort).addBlackList("0007000000000001", THIRD_USER_ID, "0441", "处理成功");
        assertEquals("9999", response.getRetCode());
    }

    /**
     * 幂等拒答（同一笔已支付成功）落在第二支，但回查确认这笔是 SUCCESS ——
     * MUST 只写 FAIL、NEVER 加黑名单，否则已付款乘客被拉黑过不了闸。
     */
    @Test
    void idempotentRejectionConfirmedSuccessIsNeverBlacklisted() {
        when(payCenterPort.requestPay(any())).thenReturn(accepted("9999", Map.of("channelOrderNo", "2026091822001")));
        when(payCenterPort.payQuery(any())).thenReturn(accepted("SUCCESS", Map.of("tradeStatus", "SUCCESS")));

        service.requestPay(request());

        verify(payTxnRepository).writeRequestResult(ORDER_NO, "FAIL", null, "2026091822001");
        verify(blacklistPort, never()).addBlackList(anyString(), anyString(), any(), anyString());
    }

    /** 回查拿不到应答时确认不了真实失败，按保守口径不加黑（漏加黑优于误加黑）。 */
    @Test
    void unconfirmableFailureIsNeverBlacklisted() {
        when(payCenterPort.requestPay(any())).thenReturn(accepted("9999", Map.of()));
        when(payCenterPort.payQuery(any())).thenReturn(new PayCenterReply.NoAnswer());

        service.requestPay(request());

        verify(blacklistPort, never()).addBlackList(anyString(), anyString(), any(), anyString());
    }

    @Test
    void rejectedReplyIsFailButNeverBlacklisted() {
        when(payCenterPort.requestPay(any())).thenReturn(new PayCenterReply.Rejected(600, null, "操作失败", "{}"));

        AlipayTripRequestPayRespDTO response = service.requestPay(request());

        verify(payTxnRepository).writeRequestResult(ORDER_NO, "FAIL", null, null);
        verify(blacklistPort, never()).addBlackList(anyString(), anyString(), any(), anyString());
        assertEquals("操作失败", response.getRetMsg());
    }

    @Test
    void noAnswerNeverWritesBackAndNeverBlacklists() {
        when(payCenterPort.requestPay(any())).thenReturn(new PayCenterReply.NoAnswer());

        AlipayTripRequestPayRespDTO response = service.requestPay(request());

        verify(payTxnRepository, never()).writeRequestResult(anyString(), anyString(), any(), any());
        verify(blacklistPort, never()).addBlackList(anyString(), anyString(), any(), anyString());
        assertEquals("调用支付中心失败", response.getRetMsg());
    }

    @Test
    void outboundExceptionNeverWritesBackButStillRecordsMessageLog() {
        when(payCenterPort.requestPay(any())).thenThrow(new IllegalStateException("连接超时"));

        AlipayTripRequestPayRespDTO response = service.requestPay(request());

        verify(payTxnRepository, never()).writeRequestResult(anyString(), anyString(), any(), any());
        verify(msgLogWriter).record(eq(ORDER_NO), eq("20260918"), eq("requestPay"), isNull(), any(), isNull(),
                org.mockito.ArgumentMatchers.anyLong(), anyString());
        assertEquals("调用支付中心失败", response.getRetMsg());
    }

    @Test
    void unsignedUserIsRejectedBeforeAnyPersistence() {
        when(alipaySignInfoMapper.selectByThirdUserIdAndChannel(THIRD_USER_ID, "ALIPAY")).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class, () -> service.requestPay(request()));

        assertEquals("用户未签约", error.getMessage());
        verify(payTxnRepository, never()).findByOrderNo(anyString());
        verify(payCenterPort, never()).requestPay(any());
    }

    @Test
    void blankThirdUserIdIsRejectedBeforeQueryingSignInfo() {
        AlipayTripRequestPayReqDTO request = request();
        request.setThirdUserId(null);

        assertThrows(BusinessException.class, () -> service.requestPay(request));
        verify(alipaySignInfoMapper, never()).selectByThirdUserIdAndChannel(any(), anyString());
    }

    @Test
    void missingTxnDateIsRejectedBeforeOutbound() {
        when(gateTxnPayClient.queryByOrderNo(ORDER_NO)).thenReturn(order(null));

        assertThrows(BusinessException.class, () -> service.requestPay(request()));
        verify(payTxnRepository, never()).openAttempt(any(), any(), anyString(), any());
        verify(payCenterPort, never()).requestPay(any());
    }

    private PayCenterReply accepted(String retCode, Map<String, Object> data) {
        return new PayCenterReply.Accepted(200, Boolean.TRUE, "ok", "{}", retCode, "处理成功", new HashMap<>(data));
    }

    private AlipayTripRequestPayReqDTO request() {
        AlipayTripRequestPayReqDTO request = new AlipayTripRequestPayReqDTO();
        request.setOrderNo(ORDER_NO);
        request.setThirdUserId(THIRD_USER_ID);
        request.setAmount(400);
        request.setIndustryType("1");
        request.setSubject("青岛地铁");
        request.setBody("地铁乘车扣费");
        request.setIndustryDetail("{\"entryStationCode\":\"0101\"}");
        return request;
    }

    private AlipaySignInfo signInfo() {
        AlipaySignInfo signInfo = new AlipaySignInfo();
        signInfo.setThirdUserId(THIRD_USER_ID);
        signInfo.setAgreementCode("20260918000001");
        signInfo.setChannelAgreementCode("20260918990001");
        signInfo.setCardId("0007000000000001");
        signInfo.setCardType("0441");
        return signInfo;
    }

    private GateTxnPayListDTO order(String txnDate) {
        GateTxnPayListDTO order = new GateTxnPayListDTO();
        order.setTxnDate(txnDate);
        return order;
    }

    private AlipayPayTxnDetail detail(String payStatus) {
        AlipayPayTxnDetail detail = new AlipayPayTxnDetail();
        detail.setOrderNo(ORDER_NO);
        detail.setTxnDate("20260918");
        detail.setPayStatus(payStatus);
        detail.setAmount(400);
        return detail;
    }
}
