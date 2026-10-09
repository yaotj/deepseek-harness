package com.chinasofti.huateng.gatetxnpay.service.impl;

import com.chinasofti.huateng.gatetxnpay.constant.GateTxnPayRetCode;
import com.chinasofti.huateng.gatetxnpay.entity.GateRetryQueue;
import com.chinasofti.huateng.gatetxnpay.entity.GateTxnPay;
import com.chinasofti.huateng.gatetxnpay.mapper.GateRetryQueueMapper;
import com.chinasofti.huateng.gatetxnpay.mapper.GateTxnPayMapper;
import com.chinasofti.huateng.model.app.RequestPayFailOrderReqDTO;
import com.chinasofti.huateng.model.app.RequestPayFailOrderResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@link UserDebitRetryService} 行为特征测试：方案 A 入队逻辑与边界。 */
@ExtendWith(MockitoExtension.class)
class UserDebitRetryServiceTest {

    @Mock
    private GateTxnPayMapper gateTxnPayMapper;

    @Mock
    private GateRetryQueueMapper gateRetryQueueMapper;

    private UserDebitRetryService userDebitRetryService;

    @BeforeEach
    void setUp() {
        // 手动构造：新增 gateRetryQueueMapper 依赖
        userDebitRetryService = new UserDebitRetryService(
                gateTxnPayMapper,
                gateRetryQueueMapper,
                200,
                1000
        );
    }

    private static GateTxnPay order(String orderNo, String cardId) {
        GateTxnPay order = new GateTxnPay();
        order.setOrderNo(orderNo);
        order.setTxnDate("20261009");
        order.setCardId(cardId);
        return order;
    }

    @Test
    void 缺thirdUserId_返回8001() {
        RequestPayFailOrderResult result = userDebitRetryService
                .requestPayFailOrder(new RequestPayFailOrderReqDTO());

        assertEquals(GateTxnPayRetCode.INVALID_PARAM, result.getRetCode());
        assertTrue(result.getRetMsg().contains("thirdUserId"));
    }

    @Test
    void 无候选_返回成功且无待重试() {
        when(gateTxnPayMapper.selectUserRetryCandidates(anyString(), anyList(), anyInt()))
                .thenReturn(Collections.emptyList());

        RequestPayFailOrderReqDTO req = new RequestPayFailOrderReqDTO();
        req.setThirdUserId("U1");
        RequestPayFailOrderResult result = userDebitRetryService.requestPayFailOrder(req);

        assertEquals(GateTxnPayRetCode.SUCCESS, result.getRetCode());
        assertTrue(result.getRetMsg().contains("无待重试"));
        // 不应入队
        verify(gateRetryQueueMapper, never()).batchInsertIgnoreDuplicate(any());
    }

    @Test
    void 命中候选_抢占成功后入队() {
        GateTxnPay o1 = order("O1", "C1");
        GateTxnPay o2 = order("O2", "C2");
        when(gateTxnPayMapper.selectUserRetryCandidates(eq("U1"), anyList(), anyInt()))
                .thenReturn(Arrays.asList(o1, o2));
        when(gateTxnPayMapper.prepareUserRetry(eq("O1"), eq("20261009"), anyString())).thenReturn(1);
        when(gateTxnPayMapper.prepareUserRetry(eq("O2"), eq("20261009"), anyString())).thenReturn(1);

        // 入队成功
        when(gateRetryQueueMapper.batchInsertIgnoreDuplicate(any())).thenReturn(2);

        RequestPayFailOrderReqDTO req = new RequestPayFailOrderReqDTO();
        req.setThirdUserId("U1");
        req.setCardNums("C1,C2 ,C1"); // 含空格与重复，应去重
        RequestPayFailOrderResult result = userDebitRetryService.requestPayFailOrder(req);

        assertEquals(GateTxnPayRetCode.SUCCESS, result.getRetCode());
        assertTrue(result.getRetMsg().contains("已加入重试队列"));
        assertTrue(result.getRetMsg().contains("共2笔"));

        // 验证入队调用
        ArgumentCaptor<List<GateRetryQueue>> queueCaptor = ArgumentCaptor.forClass(List.class);
        verify(gateRetryQueueMapper).batchInsertIgnoreDuplicate(queueCaptor.capture());
        assertEquals(2, queueCaptor.getValue().size());

        // 卡号列表应已去重为 [C1, C2]
        ArgumentCaptor<List<String>> cardCaptor = ArgumentCaptor.forClass(List.class);
        verify(gateTxnPayMapper).selectUserRetryCandidates(eq("U1"), cardCaptor.capture(), anyInt());
        assertEquals(Arrays.asList("C1", "C2"), cardCaptor.getValue());
    }

    @Test
    void 抢占失败_跳过该笔不入队() {
        GateTxnPay o1 = order("O1", "C1");
        when(gateTxnPayMapper.selectUserRetryCandidates(anyString(), anyList(), anyInt()))
                .thenReturn(Arrays.asList(o1));
        when(gateTxnPayMapper.prepareUserRetry(eq("O1"), eq("20261009"), anyString())).thenReturn(0);

        RequestPayFailOrderReqDTO req = new RequestPayFailOrderReqDTO();
        req.setThirdUserId("U1");
        RequestPayFailOrderResult result = userDebitRetryService.requestPayFailOrder(req);

        assertEquals(GateTxnPayRetCode.SUCCESS, result.getRetCode());
        assertTrue(result.getRetMsg().contains("已被处理"));
        // 不应入队
        verify(gateRetryQueueMapper, never()).batchInsertIgnoreDuplicate(any());
    }

    @Test
    void 部分抢占成功_只入队抢到的() {
        GateTxnPay o1 = order("O1", "C1");
        GateTxnPay o2 = order("O2", "C2");
        when(gateTxnPayMapper.selectUserRetryCandidates(eq("U1"), anyList(), anyInt()))
                .thenReturn(Arrays.asList(o1, o2));
        // o1 抢占成功，o2 失败（已被处理）
        when(gateTxnPayMapper.prepareUserRetry(eq("O1"), eq("20261009"), anyString())).thenReturn(1);
        when(gateTxnPayMapper.prepareUserRetry(eq("O2"), eq("20261009"), anyString())).thenReturn(0);

        when(gateRetryQueueMapper.batchInsertIgnoreDuplicate(any())).thenReturn(1);

        RequestPayFailOrderReqDTO req = new RequestPayFailOrderReqDTO();
        req.setThirdUserId("U1");
        RequestPayFailOrderResult result = userDebitRetryService.requestPayFailOrder(req);

        assertEquals(GateTxnPayRetCode.SUCCESS, result.getRetCode());
        assertTrue(result.getRetMsg().contains("共1笔"));
    }
}
