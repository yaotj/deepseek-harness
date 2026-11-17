package com.chinasofti.huateng.alipay.paysign.service.impl.refund;

import com.chinasofti.huateng.alipay.paysign.config.PayCenterProperties;
import com.chinasofti.huateng.alipay.paysign.entity.AlipayPayTxnDetail;
import com.chinasofti.huateng.alipay.paysign.entity.AlipayRefundTxnDetail;
import com.chinasofti.huateng.alipay.paysign.exception.BusinessException;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipayPayTxnDetailMapper;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipayRefundTxnDetailMapper;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipaySignInfoMapper;
import com.chinasofti.huateng.alipay.paysign.port.PayCenterPort;
import com.chinasofti.huateng.alipay.paysign.port.PayCenterReply;
import com.chinasofti.huateng.alipay.paysign.service.impl.support.AlipayPayCenterMsgLogWriter;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfo;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripTxnRefundReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripTxnRefundRespDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


class AlipayTxnRefundServiceTest {

    private static final String ORDER_NO = "GT20260920000000001";
    private static final String CHANNEL_AGREEMENT_NO = "2088302232551032";
    private static final String CARD_ID = "2607031119542741";

    private AlipayPayTxnDetailMapper payTxnDetailMapper;
    private AlipayRefundTxnDetailMapper refundTxnDetailMapper;
    private AlipaySignInfoMapper signInfoMapper;
    private PayCenterPort payCenterPort;
    private PayCenterProperties payCenterProperties;
    private AlipayPayCenterMsgLogWriter msgLogWriter;

    private AlipayTxnRefundService service;

    @BeforeEach
    void setUp() {
        payTxnDetailMapper = mock(AlipayPayTxnDetailMapper.class);
        refundTxnDetailMapper = mock(AlipayRefundTxnDetailMapper.class);
        signInfoMapper = mock(AlipaySignInfoMapper.class);
        payCenterPort = mock(PayCenterPort.class);
        payCenterProperties = mock(PayCenterProperties.class);
        msgLogWriter = mock(AlipayPayCenterMsgLogWriter.class);

        service = new AlipayTxnRefundService(payTxnDetailMapper, refundTxnDetailMapper, signInfoMapper,
                payCenterPort, payCenterProperties, msgLogWriter);

        when(payTxnDetailMapper.selectByOrderNo(ORDER_NO)).thenReturn(payTxn("SUCCESS", 200, 0));
        when(refundTxnDetailMapper.countByOrderNoAndStatus(ORDER_NO, "PROCESSING")).thenReturn(0);
        when(refundTxnDetailMapper.markRequesting(anyString(), anyString())).thenReturn(1);
        when(signInfoMapper.selectByChannelAgreementCode(CHANNEL_AGREEMENT_NO)).thenReturn(signInfo());
        when(payCenterProperties.getRefundNotifyUrl()).thenReturn("http://itp/api/payment/refundNotify");
    }

    @Test
    void missingPayTxnIsRejectedBeforePersistence() {
        when(payTxnDetailMapper.selectByOrderNo(ORDER_NO)).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class, () -> service.requestRefund(request(null)));

        assertEquals("原支付记录不存在", error.getMessage());
        verify(refundTxnDetailMapper, never()).insert(any());
        verify(payCenterPort, never()).requestRefund(any());
    }

    @Test
    void unsettledPayTxnIsRejected() {
        when(payTxnDetailMapper.selectByOrderNo(ORDER_NO)).thenReturn(payTxn("PROCESSING", 200, 0));

        BusinessException error = assertThrows(BusinessException.class, () -> service.requestRefund(request(null)));

        assertEquals("原支付订单未支付成功", error.getMessage());
        verify(refundTxnDetailMapper, never()).insert(any());
    }

    @Test
    void processingRefundIsRejectedBeforePersistenceAndOutbound() {
        when(refundTxnDetailMapper.countByOrderNoAndStatus(ORDER_NO, "PROCESSING")).thenReturn(1);

        BusinessException error = assertThrows(BusinessException.class, () -> service.requestRefund(request(null)));

        assertEquals("该订单存在处理中的退款，请先确认上一笔结果", error.getMessage());
        verify(refundTxnDetailMapper, never()).insert(any());
        verify(payCenterPort, never()).requestRefund(any());
    }

    @Test
    void refundAmountBeyondAvailableIsRejected() {
        when(payTxnDetailMapper.selectByOrderNo(ORDER_NO)).thenReturn(payTxn("SUCCESS", 200, 150));

        BusinessException error = assertThrows(BusinessException.class, () -> service.requestRefund(request("100")));

        assertEquals("退款金额超出可退金额", error.getMessage());
        verify(refundTxnDetailMapper, never()).insert(any());
    }

    @Test
    void missingSignInfoIsRejectedSoNoEmptyCardNumGoesOut() {
        when(signInfoMapper.selectByChannelAgreementCode(CHANNEL_AGREEMENT_NO)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.requestRefund(request(null)));

        verify(refundTxnDetailMapper, never()).insert(any());
        verify(payCenterPort, never()).requestRefund(any());
    }


    @Test
    void acceptedKeepsProcessingAndNeverRefreshesSummary() {
        when(payCenterPort.requestRefund(any())).thenReturn(acceptedWithoutData());

        AlipayTripTxnRefundRespDTO response = service.requestRefund(request(null));

        assertEquals(FepAppErrorCodeEnum.SUCCESS.getCode(), response.getRetCode());
        assertNotNull(response.getRefundOrderNo());
        verify(payTxnDetailMapper, never()).updateRefundSummary(anyString());

        InOrder order = inOrder(refundTxnDetailMapper, payCenterPort);
        order.verify(refundTxnDetailMapper).insert(any());
        order.verify(refundTxnDetailMapper).markRequesting(anyString(), anyString());
        order.verify(payCenterPort).requestRefund(any());

        ArgumentCaptor<AlipayRefundTxnDetail> opened = ArgumentCaptor.forClass(AlipayRefundTxnDetail.class);
        verify(refundTxnDetailMapper).insert(opened.capture());
        assertEquals("INIT", opened.getValue().getRefundStatus());
        assertEquals(200, opened.getValue().getRefundAmount());
        assertEquals(ORDER_NO, opened.getValue().getOrderNo());

        ArgumentCaptor<AlipayRefundTxnDetail> settled = statusWrites();
        assertEquals("PROCESSING", settled.getValue().getRefundStatus());
        assertEquals(settled.getValue().getRefundOrderNo(), settled.getValue().getMerchantRefundNo());
    }

    @Test
    void acceptedWithDataBackfillsRefundNumbers() {
        Map<String, Object> data = new HashMap<>();
        data.put("merchantRefundNo", "M001");
        data.put("refundNo", "P001");
        data.put("channelRefundNo", "C001");
        data.put("refundTime", "20260920181917");
        when(payCenterPort.requestRefund(any()))
                .thenReturn(new PayCenterReply.Accepted(200, Boolean.TRUE, "操作成功", "{}", null, null, data));

        service.requestRefund(request(null));

        ArgumentCaptor<AlipayRefundTxnDetail> settled = statusWrites();
        assertEquals("M001", settled.getValue().getMerchantRefundNo());
        assertEquals("P001", settled.getValue().getRefundNo());
        assertEquals("C001", settled.getValue().getChannelRefundNo());
        assertEquals("20260920181917", settled.getValue().getRefundTime());
    }

    @Test
    void noAnswerKeepsProcessingAndNeverRefreshesSummary() {
        when(payCenterPort.requestRefund(any())).thenReturn(new PayCenterReply.NoAnswer());

        AlipayTripTxnRefundRespDTO response = service.requestRefund(request(null));

        assertEquals(FepAppErrorCodeEnum.SYSTEM_ERROR.getCode(), response.getRetCode());
        verify(payTxnDetailMapper, never()).updateRefundSummary(anyString());

        ArgumentCaptor<AlipayRefundTxnDetail> settled = statusWrites();
        assertEquals("PROCESSING", settled.getValue().getRefundStatus());
    }

    private ArgumentCaptor<AlipayRefundTxnDetail> statusWrites() {
        ArgumentCaptor<AlipayRefundTxnDetail> captor = ArgumentCaptor.forClass(AlipayRefundTxnDetail.class);
        verify(refundTxnDetailMapper).updateRequestResult(captor.capture());
        return captor;
    }

    private AlipayTripTxnRefundReqDTO request(String refundAmount) {
        AlipayTripTxnRefundReqDTO request = new AlipayTripTxnRefundReqDTO();
        request.setOrderNo(ORDER_NO);
        request.setRefundAmount(refundAmount);
        request.setRefundReason("运营人工退款");
        return request;
    }

    private AlipayPayTxnDetail payTxn(String payStatus, int amount, int refunded) {
        AlipayPayTxnDetail detail = new AlipayPayTxnDetail();
        detail.setOrderNo(ORDER_NO);
        detail.setPayStatus(payStatus);
        detail.setAmount(amount);
        detail.setRefundAmount(refunded);
        detail.setChannelAgreementNo(CHANNEL_AGREEMENT_NO);
        detail.setTxnDate("20260920");
        return detail;
    }

    private AlipaySignInfo signInfo() {
        AlipaySignInfo signInfo = new AlipaySignInfo();
        signInfo.setCardId(CARD_ID);
        signInfo.setChannelAgreementCode(CHANNEL_AGREEMENT_NO);
        return signInfo;
    }

    private PayCenterReply.Accepted acceptedWithoutData() {
        return new PayCenterReply.Accepted(200, Boolean.TRUE, "操作成功", "{\"code\":200,\"msg\":\"操作成功\"}",
                null, null, new HashMap<>());
    }
}
