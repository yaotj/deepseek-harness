package com.chinasofti.huateng.alipay.paysign.service.impl;

import com.chinasofti.huateng.alipay.paysign.service.impl.notify.PaymentNotifyAdapter;
import com.chinasofti.huateng.alipay.paysign.service.impl.support.BizDataBuilder;
import com.chinasofti.huateng.alipay.paysign.service.impl.support.IndustryDetailEnricher;
import com.chinasofti.huateng.alipay.paysign.service.impl.payment.PaymentQueryService;
import com.chinasofti.huateng.alipay.paysign.service.impl.payment.PaymentRefundService;
import com.chinasofti.huateng.alipay.paysign.service.impl.payment.PaymentRequestService;
import com.chinasofti.huateng.alipay.paysign.service.impl.support.RefundAmountCalculator;

import com.chinasofti.huateng.alipay.paysign.config.PayCenterProperties;
import com.chinasofti.huateng.alipay.paysign.entity.AlipayPayLog;
import com.chinasofti.huateng.alipay.paysign.exception.BusinessException;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipayPayCallbackLogMapper;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipayPayLogMapper;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipayRefundLogMapper;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipaySignInfoMapper;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripPayQueryReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripRequestPayReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripRequestRefundReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripPayQueryRespDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripRequestPayRespDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripRequestRefundRespDTO;
import com.chinasofti.huateng.alipay.paysign.port.PayCenterPort;
import com.chinasofti.huateng.alipay.paysign.port.PayCenterReply;
import com.chinasofti.huateng.alipay.paysign.port.BlacklistPort;
import com.chinasofti.huateng.alipay.paysign.port.DebitSyncPort;
import com.chinasofti.huateng.alipay.paysign.util.PayCenterClient;
import java.util.Map;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfo;
import com.chinasofti.huateng.rpc.outcome.RpcOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DuplicateKeyException;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


class AlipayPaymentCharacterizationTest {

    private static final String ORDER_NO = "GT20260917021300000000001";
    private static final String THIRD_USER_ID = "2088100000000001";
    private static final String CARD_ID = "0007000000000001";
    private static final String AGREEMENT_CODE = "AG20260917000001";
    private static final String CHANNEL_AGREEMENT_CODE = "20260917990001";

    private AlipaySignInfoMapper signInfoMapper;
    private AlipayPayLogMapper payLogMapper;
    private AlipayRefundLogMapper refundLogMapper;
    private PayCenterPort payCenterPort;
    private BlacklistPort blacklistPort;
    private BizDataBuilder bizDataBuilder;
    private IndustryDetailEnricher industryDetailEnricher;

    private PaymentRequestService requestService;
    private PaymentQueryService queryService;
    private PaymentRefundService refundService;

    @BeforeEach
    void setUp() throws ReflectiveOperationException {
        signInfoMapper = mock(AlipaySignInfoMapper.class);
        payLogMapper = mock(AlipayPayLogMapper.class);
        refundLogMapper = mock(AlipayRefundLogMapper.class);
        payCenterPort = mock(PayCenterPort.class);
        when(payCenterPort.requestPay(any())).thenReturn(new PayCenterReply.NoAnswer());
        when(payCenterPort.payQuery(any())).thenReturn(new PayCenterReply.NoAnswer());
        when(payCenterPort.requestRefund(any())).thenReturn(new PayCenterReply.NoAnswer());
        blacklistPort = mock(BlacklistPort.class);
        when(blacklistPort.addBlackList(anyString(), anyString(), any(), any())).thenReturn(new RpcOutcome.Ok());
        bizDataBuilder = mock(BizDataBuilder.class);
        industryDetailEnricher = mock(IndustryDetailEnricher.class);

        requestService = new PaymentRequestService();
        inject(requestService, "alipaySignInfoMapper", signInfoMapper);
        inject(requestService, "payCenterPort", payCenterPort);
        inject(requestService, "payCenterProperties", mock(PayCenterProperties.class));
        inject(requestService, "paymentNotifyAdapter", mock(PaymentNotifyAdapter.class));
        inject(requestService, "industryDetailEnricher", industryDetailEnricher);
        inject(requestService, "bizDataBuilder", bizDataBuilder);
        inject(requestService, "blacklistPort", blacklistPort);

        queryService = new PaymentQueryService();
        inject(queryService, "alipayPayLogMapper", payLogMapper);
        inject(queryService, "alipayPayCallbackLogMapper", mock(AlipayPayCallbackLogMapper.class));
        inject(queryService, "payCenterPort", payCenterPort);
        inject(queryService, "debitSyncPort", mock(DebitSyncPort.class));

        refundService = new PaymentRefundService();
        inject(refundService, "alipayPayLogMapper", payLogMapper);
        inject(refundService, "alipayRefundLogMapper", refundLogMapper);
        inject(refundService, "alipaySignInfoMapper", signInfoMapper);
        inject(refundService, "refundAmountCalculator", new RefundAmountCalculator());
        inject(refundService, "payCenterPort", payCenterPort);
    }


    @Test
    void unsignedUserIsRejectedBeforeAnyGatewayCall() {
        when(signInfoMapper.selectByThirdUserIdAndChannel(THIRD_USER_ID, "ALIPAY")).thenReturn(null);

        BusinessException thrown = assertThrows(BusinessException.class,
                () -> requestService.requestPay(payRequest()));

        assertEquals(FepAppErrorCodeEnum.USER_NOT_SIGNED.getCode(), thrown.getCode(),
                "未签约 MUST 返 8011");
        verify(payCenterPort, never()).requestPay(any());
    }

    @Test
    void requestSignSeqIsOverwrittenWithLocalAgreementCode() {
        when(signInfoMapper.selectByThirdUserIdAndChannel(THIRD_USER_ID, "ALIPAY")).thenReturn(signInfo());
        when(industryDetailEnricher.enrich(anyString(), anyString())).thenReturn("{}");

        AlipayTripRequestPayReqDTO request = payRequest();
        request.setRequestSignSeq("上游送来的值-会被覆盖");
        requestService.requestPay(request);

        ArgumentCaptor<AlipayTripRequestPayReqDTO> captor =
                ArgumentCaptor.forClass(AlipayTripRequestPayReqDTO.class);
        verify(bizDataBuilder).build(captor.capture(), any(), any());
        assertEquals(AGREEMENT_CODE, captor.getValue().getRequestSignSeq(),
                "requestSignSeq MUST 取本地签约记录的 AGREEMENT_CODE，NEVER 相信上游上送值");
    }

    @Test
    void businessRejectionAddsBlackList() {
        when(signInfoMapper.selectByThirdUserIdAndChannel(THIRD_USER_ID, "ALIPAY")).thenReturn(signInfo());
        when(industryDetailEnricher.enrich(anyString(), anyString())).thenReturn("{}");
        when(payCenterPort.requestPay(any())).thenReturn(accepted(Map.of("retCode", "BIZ_REJECT", "retMsg", "余额不足")));

        AlipayTripRequestPayRespDTO response = requestService.requestPay(payRequest());

        assertEquals(FepAppErrorCodeEnum.FAIL.getCode(), response.getRetCode());
        assertEquals("余额不足", response.getRetMsg());
        verify(blacklistPort).addBlackList(eq(CARD_ID), eq(THIRD_USER_ID), any(), any());
    }

    @Test
    void transportFailureNeverAddsBlackList() {
        when(signInfoMapper.selectByThirdUserIdAndChannel(THIRD_USER_ID, "ALIPAY")).thenReturn(signInfo());
        when(industryDetailEnricher.enrich(anyString(), anyString())).thenReturn("{}");
        when(payCenterPort.requestPay(any())).thenReturn(rejected(600, "操作失败"));

        AlipayTripRequestPayRespDTO response = requestService.requestPay(payRequest());

        assertEquals(FepAppErrorCodeEnum.FAIL.getCode(), response.getRetCode());
        verify(blacklistPort, never()).addBlackList(anyString(), anyString(), any(), any());
    }

    @Test
    void nullGatewayResponseNeverAddsBlackList() {
        when(signInfoMapper.selectByThirdUserIdAndChannel(THIRD_USER_ID, "ALIPAY")).thenReturn(signInfo());
        when(industryDetailEnricher.enrich(anyString(), anyString())).thenReturn("{}");
        when(payCenterPort.requestPay(any())).thenReturn(new PayCenterReply.NoAnswer());

        AlipayTripRequestPayRespDTO response = requestService.requestPay(payRequest());

        assertEquals(FepAppErrorCodeEnum.SYSTEM_ERROR.getCode(), response.getRetCode(),
                "网关无响应 MUST 返 9001，与「业务失败」区分开");
        verify(blacklistPort, never()).addBlackList(anyString(), anyString(), any(), any());
    }

    @Test
    void businessSuccessReturnsZeroAndNeverAddsBlackList() {
        when(signInfoMapper.selectByThirdUserIdAndChannel(THIRD_USER_ID, "ALIPAY")).thenReturn(signInfo());
        when(industryDetailEnricher.enrich(anyString(), anyString())).thenReturn("{}");
        when(payCenterPort.requestPay(any())).thenReturn(accepted(Map.of("retCode", "SUCCESS")));

        AlipayTripRequestPayRespDTO response = requestService.requestPay(payRequest());

        assertEquals(FepAppErrorCodeEnum.SUCCESS.getCode(), response.getRetCode());
        assertEquals(ORDER_NO, response.getOrderNo());
        verify(blacklistPort, never()).addBlackList(anyString(), anyString(), any(), any());
    }

    @Test
    void requestPayHasNoPayLogWriterAtAll_currentDefect() {
        boolean hasPayLogMapper = Arrays.stream(PaymentRequestService.class.getDeclaredFields())
                .anyMatch(field -> AlipayPayLogMapper.class.equals(field.getType()));

        assertFalse(hasPayLogMapper,
                "钉住缺陷现状：requestPay 完全不写 ALIPAY_PAY_LOG，因此该类连 mapper 都没注。"
                        + "补落库那一批 MUST 连同本断言与对应 ADR 一起改，NEVER 只把断言删掉");
    }


    @Test
    void payQueryMissingOrderIsRejectedBeforeGatewayCall() {
        when(payLogMapper.selectByOrderNo(ORDER_NO)).thenReturn(null);

        BusinessException thrown = assertThrows(BusinessException.class,
                () -> queryService.payQuery(payQueryRequest()));

        assertEquals(FepAppErrorCodeEnum.FAIL.getCode(), thrown.getCode());
        verify(payCenterPort, never()).payQuery(any());
    }

    @Test
    void payQueryWritesBackThroughNonTerminalWhitelist() {
        when(payLogMapper.selectByOrderNo(ORDER_NO)).thenReturn(payLog("PROCESSING"));
        when(payCenterPort.payQuery(any())).thenReturn(accepted(Map.of(
                "retCode", "SUCCESS", "tradeNo", "2026091722001", "totalAmount", "400")));
        when(payLogMapper.updatePayQueryResultIfNotSuccess(eq(ORDER_NO), eq("SUCCESS"), any(), any(), any(),
                eq(FepAppErrorCodeEnum.SUCCESS.getCode()), any())).thenReturn(1);

        AlipayTripPayQueryRespDTO response = queryService.payQuery(payQueryRequest());

        assertEquals(FepAppErrorCodeEnum.SUCCESS.getCode(), response.getRetCode());
        verify(payLogMapper).updatePayQueryResultIfNotSuccess(eq(ORDER_NO), eq("SUCCESS"), any(), any(), any(),
                any(), any());
        verify(payLogMapper, times(2)).selectByOrderNo(ORDER_NO);
    }

    @Test
    void terminalLocalStatusIsNeverOverwrittenByGatewayVerdict() {
        when(payLogMapper.selectByOrderNo(ORDER_NO)).thenReturn(payLog("SUCCESS"));
        when(payCenterPort.payQuery(any())).thenReturn(accepted(Map.of("retCode", "BIZ_FAIL")));
        when(payLogMapper.updatePayQueryResultIfNotSuccess(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(0);

        AlipayTripPayQueryRespDTO response = queryService.payQuery(payQueryRequest());

        assertEquals(FepAppErrorCodeEnum.SUCCESS.getCode(), response.getRetCode(),
                "口径冲突只告警、MUST 仍对上游返 0000");
        assertEquals("SUCCESS", response.getTradeStatus(),
                "本地已是终态 MUST 拒绝被支付中心的 FAIL 覆盖");
        verify(payLogMapper, times(3)).selectByOrderNo(ORDER_NO);
    }

    @Test
    void payQueryTransportFailureKeepsLocalRowUntouched() {
        when(payLogMapper.selectByOrderNo(ORDER_NO)).thenReturn(payLog("PROCESSING"));
        when(payCenterPort.payQuery(any())).thenReturn(new PayCenterReply.NoAnswer());

        AlipayTripPayQueryRespDTO response = queryService.payQuery(payQueryRequest());

        assertEquals(FepAppErrorCodeEnum.SUCCESS.getCode(), response.getRetCode());
        assertEquals("PROCESSING", response.getTradeStatus());
        verify(payLogMapper, never())
                .updatePayQueryResultIfNotSuccess(any(), any(), any(), any(), any(), any(), any());
    }


    @Test
    void processingRefundShortCircuitsBeforeInsertAndGateway() {
        when(payLogMapper.selectByOrderNo(ORDER_NO)).thenReturn(payLog("SUCCESS"));
        when(refundLogMapper.countByOrderNoAndStatus(ORDER_NO, "PROCESSING")).thenReturn(1);

        BusinessException thrown = assertThrows(BusinessException.class,
                () -> refundService.requestRefund(refundRequest()));

        assertEquals(FepAppErrorCodeEnum.FAIL.getCode(), thrown.getCode());
        verify(refundLogMapper, never()).insert(any());
        verify(payCenterPort, never()).requestRefund(any());
    }

    @Test
    void unpaidOriginalOrderIsRejected() {
        when(payLogMapper.selectByOrderNo(ORDER_NO)).thenReturn(payLog("PROCESSING"));

        BusinessException thrown = assertThrows(BusinessException.class,
                () -> refundService.requestRefund(refundRequest()));

        assertEquals(FepAppErrorCodeEnum.FAIL.getCode(), thrown.getCode());
        verify(refundLogMapper, never()).countByOrderNoAndStatus(any(), any());
    }

    @Test
    void uniqueIndexConflictIsReportedAsRepeatSubmission() {
        givenRefundPreconditions();
        when(refundLogMapper.insert(any()))
                .thenThrow(new IllegalStateException("包一层", new DuplicateKeyException("UK_ARL_REFUND_ORDER_NO")));

        BusinessException thrown = assertThrows(BusinessException.class,
                () -> refundService.requestRefund(refundRequest()));

        assertEquals(FepAppErrorCodeEnum.FAIL.getCode(), thrown.getCode(),
                "唯一索引冲突 MUST 沿 cause 链判定出来，NEVER 只看最外层异常类名");
        verify(payCenterPort, never()).requestRefund(any());
    }

    @Test
    void refundTransportFailureKeepsProcessingAndSkipsSummary() {
        givenRefundPreconditions();
        when(payCenterPort.requestRefund(any())).thenReturn(rejected(600, null));

        AlipayTripRequestRefundRespDTO response = refundService.requestRefund(refundRequest());

        assertEquals(FepAppErrorCodeEnum.SYSTEM_ERROR.getCode(), response.getRetCode(),
                "退款结果未知 MUST 返 9001，NEVER 报成失败");
        verify(refundLogMapper).updateRefundStatus(anyString(), eq("PROCESSING"), eq("INIT"), any(), any(), any());
        verify(payLogMapper, never()).updateRefundSummary(any());
    }

    @Test
    void refundSuccessConvergesDetailBeforeRecomputingSummary() {
        givenRefundPreconditions();
        when(payCenterPort.requestRefund(any())).thenReturn(accepted(Map.of("retCode", "SUCCESS")));
        when(payLogMapper.updateRefundSummary(ORDER_NO)).thenReturn(1);

        AlipayTripRequestRefundRespDTO response = refundService.requestRefund(refundRequest());

        assertEquals(FepAppErrorCodeEnum.SUCCESS.getCode(), response.getRetCode());
        InOrder order = inOrder(refundLogMapper, payLogMapper);
        order.verify(refundLogMapper).updateRefundStatus(anyString(), eq("SUCCESS"),
                eq(FepAppErrorCodeEnum.SUCCESS.getCode()), any(), any(), any());
        order.verify(payLogMapper).updateRefundSummary(ORDER_NO);
    }

    @Test
    void refundBusinessFailureConvergesToFailAndSkipsSummary() {
        givenRefundPreconditions();
        when(payCenterPort.requestRefund(any())).thenReturn(accepted(Map.of("retCode", "BIZ_REJECT", "retMsg", "原交易不可退")));

        AlipayTripRequestRefundRespDTO response = refundService.requestRefund(refundRequest());

        assertEquals(FepAppErrorCodeEnum.FAIL.getCode(), response.getRetCode());
        assertEquals("原交易不可退", response.getRetMsg());
        verify(refundLogMapper).updateRefundStatus(anyString(), eq("FAIL"),
                eq(FepAppErrorCodeEnum.FAIL.getCode()), any(), any(), any());
        verify(payLogMapper, never()).updateRefundSummary(any());
    }

    @Test
    void refundAmountBeyondAvailableIsRejectedBeforeInsert() {
        AlipayPayLog paid = payLog("SUCCESS");
        paid.setRefundAmount("400");
        when(payLogMapper.selectByOrderNo(ORDER_NO)).thenReturn(paid);
        when(refundLogMapper.countByOrderNoAndStatus(ORDER_NO, "PROCESSING")).thenReturn(0);
        when(signInfoMapper.selectByCardIdAndChannel(CARD_ID, "ALIPAY")).thenReturn(signInfo());

        assertThrows(IllegalArgumentException.class, () -> refundService.requestRefund(refundRequest()),
                "可退金额已为 0 MUST 在落明细之前拒掉");
        verify(refundLogMapper, never()).insert(any());
    }


    private void givenRefundPreconditions() {
        when(payLogMapper.selectByOrderNo(ORDER_NO)).thenReturn(payLog("SUCCESS"));
        when(refundLogMapper.countByOrderNoAndStatus(ORDER_NO, "PROCESSING")).thenReturn(0);
        when(signInfoMapper.selectByCardIdAndChannel(CARD_ID, "ALIPAY")).thenReturn(signInfo());
    }

    private AlipaySignInfo signInfo() {
        AlipaySignInfo signInfo = new AlipaySignInfo();
        signInfo.setAgreementCode(AGREEMENT_CODE);
        signInfo.setChannelAgreementCode(CHANNEL_AGREEMENT_CODE);
        signInfo.setThirdUserId(THIRD_USER_ID);
        signInfo.setCardId(CARD_ID);
        signInfo.setCardType("0441");
        signInfo.setChannel("ALIPAY");
        signInfo.setSignStatus("SIGNED");
        return signInfo;
    }

    private AlipayPayLog payLog(String payStatus) {
        AlipayPayLog payLog = new AlipayPayLog();
        payLog.setPaySeq("PS20260917000001");
        payLog.setOrderNo(ORDER_NO);
        payLog.setThirdUserId(THIRD_USER_ID);
        payLog.setCardId(CARD_ID);
        payLog.setPayAmount("400");
        payLog.setPayStatus(payStatus);
        payLog.setTradeNo("2026091700001");
        payLog.setTransTime("2026-09-17 02:13:00");
        payLog.setResultMsg("处理中");
        return payLog;
    }

    private AlipayTripRequestPayReqDTO payRequest() {
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

    private AlipayTripPayQueryReqDTO payQueryRequest() {
        AlipayTripPayQueryReqDTO request = new AlipayTripPayQueryReqDTO();
        request.setOrderNo(ORDER_NO);
        request.setChannelAgreementNo(CHANNEL_AGREEMENT_CODE);
        return request;
    }

    private AlipayTripRequestRefundReqDTO refundRequest() {
        AlipayTripRequestRefundReqDTO request = new AlipayTripRequestRefundReqDTO();
        request.setOrderNo(ORDER_NO);
        return request;
    }


    private PayCenterReply accepted(Map<String, Object> data) {
        return new PayCenterReply.Accepted(200, Boolean.TRUE, null, "{\"code\":200}",
                (String) data.get("retCode"), (String) data.get("retMsg"), data);
    }

    private PayCenterReply rejected(Integer code, String msg) {
        return new PayCenterReply.Rejected(code, null, msg, "{\"code\":" + code + "}");
    }

    private static void inject(Object target, String fieldName, Object value) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
