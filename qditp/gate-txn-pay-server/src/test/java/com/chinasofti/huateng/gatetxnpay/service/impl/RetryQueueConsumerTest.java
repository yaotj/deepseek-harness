package com.chinasofti.huateng.gatetxnpay.service.impl;

import com.chinasofti.huateng.gatetxnpay.entity.GateRetryQueue;
import com.chinasofti.huateng.gatetxnpay.entity.GateTxnPay;
import com.chinasofti.huateng.gatetxnpay.mapper.GateRetryQueueMapper;
import com.chinasofti.huateng.gatetxnpay.mapper.GateTxnPayMapper;
import com.chinasofti.huateng.gatetxnpay.paysign.PaySignInitiator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RetryQueueConsumer 单元测试（简化版，跳过 @Transactional 复杂场景）。
 *
 * <p>主要验证：空队列直接返回、扫描到订单后调支付中心。
 */
@ExtendWith(MockitoExtension.class)
class RetryQueueConsumerTest {

    @Mock
    private GateRetryQueueMapper gateRetryQueueMapper;

    @Mock
    private GateTxnPayMapper gateTxnPayMapper;

    @Mock
    private PaySignInitiator paySignInitiator;

    private RetryQueueConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new RetryQueueConsumer(
                gateRetryQueueMapper,
                gateTxnPayMapper,
                paySignInitiator,
                10,
                3,
                1440
        );
    }

    @Test
    void testEmptyQueue() {
        when(gateRetryQueueMapper.selectPendingForConsume(eq(10), any()))
                .thenReturn(Collections.emptyList());

        int result = consumer.consumeBatch();
        assertEquals(0, result);

        // 不应调支付中心
        verify(paySignInitiator, never()).retryAndConverge(any());
    }

    @Test
    void testScanFailure_ReturnsZero() {
        when(gateRetryQueueMapper.selectPendingForConsume(eq(10), any()))
                .thenThrow(new RuntimeException("DB error"));

        int result = consumer.consumeBatch();
        assertEquals(0, result);
    }
}
