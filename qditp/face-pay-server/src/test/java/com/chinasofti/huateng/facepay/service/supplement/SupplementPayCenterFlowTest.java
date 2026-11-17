package com.chinasofti.huateng.facepay.service.supplement;

import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterClient;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterMessageFactory;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterProperties;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterRequest;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterResult;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterResults;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterStatus;
import com.chinasofti.huateng.facepay.entity.SupplementOrder;
import com.chinasofti.huateng.facepay.entity.SupplementOrderItem;
import com.chinasofti.huateng.facepay.mapper.SupplementOrderMapper;
import com.chinasofti.huateng.model.pay.GateTxnPayDebitConvergeReqDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayDebitConvergeRespDTO;
import com.chinasofti.huateng.rpc.pay.GateTxnPayClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SupplementPayCenterFlowTest {

    private static final String SUP_ORDER = "SP20260915120000CARD01";
    private static final String ORIG_ORDER = "GTP202609150001";
    private static final String ORIG_TXN_DATE = "20260915";

    @Mock private PayCenterClient payCenterClient;
    @Mock private PayCenterMessageFactory messageFactory;
    @Mock private SupplementOrderMapper supplementOrderMapper;
    @Mock private GateTxnPayClient gateTxnPayClient;
    @Mock private PayCenterProperties payCenterProperties;
    @Mock private PayCenterRequest payRequest;
    @Mock private PayCenterRequest queryRequest;

    private SupplementPayCenterFlow flow;

    @BeforeEach
    void setUp() {
        flow = new SupplementPayCenterFlow(payCenterClient, messageFactory, supplementOrderMapper, gateTxnPayClient);
        when(payCenterClient.properties()).thenReturn(payCenterProperties);
        when(payCenterProperties.getPayUrl()).thenReturn("http://paycenter/pay");
        when(payCenterProperties.getQueryUrl()).thenReturn("http://paycenter/query");
    }

    private static GateTxnPayDebitConvergeRespDTO convergeResp(String retCode, Boolean converged,
                                                               String debitStatus) {
        GateTxnPayDebitConvergeRespDTO resp = new GateTxnPayDebitConvergeRespDTO();
        resp.setRetCode(retCode);
        resp.setOrigOrderNo(ORIG_ORDER);
        resp.setConverged(converged);
        resp.setDebitStatus(debitStatus);
        return resp;
    }


    @Nested
    class PreOrder {

        private SupplementOrder order;

        @BeforeEach
        void setUp() {
            order = new SupplementOrder();
            order.setOrderNo(SUP_ORDER);
            order.setPayStatus("INIT");
            order.setTotalAmount(800L);
            order.setOrderCount(2);
            order.setPaymentVendor("WECHAT");
        }

        @Test
        void acceptedUpdatesPrepayResult() {
            PayCenterResult result = PayCenterResults.answered("0", null, "PAY_INFO_XXX");
            when(messageFactory.buildPayRequest(any(), any())).thenReturn(payRequest);
            when(payCenterClient.execute(eq("http://paycenter/pay"), eq(payRequest))).thenReturn(result);
            when(supplementOrderMapper.updatePrepayResult(eq(SUP_ORDER), any(), anyString(), anyString())).thenReturn(1);

            var outcome = flow.preOrder(order);

            assertInstanceOf(com.chinasofti.huateng.rpc.outcome.RpcOutcome.Ok.class, outcome);
            verify(supplementOrderMapper).updatePrepayResult(eq(SUP_ORDER), org.mockito.ArgumentMatchers.isNull(), eq("PC20260914001"), eq("PAY_INFO_XXX"));
        }

        @Test
        void updatePrepayResultZeroRowsIsIdempotent() {
            PayCenterResult result = PayCenterResults.answered("0", null, "PAY_INFO_XXX");
            when(messageFactory.buildPayRequest(any(), any())).thenReturn(payRequest);
            when(payCenterClient.execute(eq("http://paycenter/pay"), eq(payRequest))).thenReturn(result);
            when(supplementOrderMapper.updatePrepayResult(anyString(), anyString(), anyString(), anyString())).thenReturn(0);

            var outcome = flow.preOrder(order);

            assertInstanceOf(com.chinasofti.huateng.rpc.outcome.RpcOutcome.Ok.class, outcome);
        }

        @Test
        void transportFailureNeverTouchesDb() {
            when(messageFactory.buildPayRequest(any(), any())).thenReturn(payRequest);
            when(payCenterClient.execute(any(), eq(payRequest))).thenReturn(PayCenterResults.transportFailed("read timeout"));

            var outcome = flow.preOrder(order);

            assertInstanceOf(com.chinasofti.huateng.rpc.outcome.RpcOutcome.Unreachable.class, outcome);
            verify(supplementOrderMapper, never()).updatePrepayResult(anyString(), anyString(), anyString(), anyString());
        }

        @Test
        void bizRejectedNeverUpdates() {
            PayCenterResult result = PayCenterResults.answered("9999", null, null);
            when(messageFactory.buildPayRequest(any(), any())).thenReturn(payRequest);
            when(payCenterClient.execute(any(), eq(payRequest))).thenReturn(result);

            var outcome = flow.preOrder(order);

            assertInstanceOf(com.chinasofti.huateng.rpc.outcome.RpcOutcome.BizRejected.class, outcome);
            verify(supplementOrderMapper, never()).updatePrepayResult(anyString(), anyString(), anyString(), anyString());
        }
    }


    @Nested
    class HandlePayNotice {

        private SupplementOrder order;
        private SupplementOrderItem item;

        @BeforeEach
        void setUp() {
            order = new SupplementOrder();
            order.setOrderNo(SUP_ORDER);
            order.setPayStatus("PROCESSING");

            item = new SupplementOrderItem();
            item.setOrderNo(SUP_ORDER);
            item.setOrigOrderNo(ORIG_ORDER);
            item.setOrigTxnDate(ORIG_TXN_DATE);
            item.setSettleStatus("PENDING");
        }

        @Test
        void missingOrderIsSilentlyIgnored() {
            when(supplementOrderMapper.selectByOrderNo(SUP_ORDER)).thenReturn(null);

            flow.handlePayNotice(SUP_ORDER);

            verifyNoInteractions(payCenterClient);
            verifyNoInteractions(gateTxnPayClient);
        }

        @Test
        void successConvergesFullChain() {
            when(supplementOrderMapper.selectByOrderNo(SUP_ORDER)).thenReturn(order);
            when(messageFactory.buildQueryRequest(SUP_ORDER)).thenReturn(queryRequest);
            when(payCenterClient.execute(any(), eq(queryRequest))).thenReturn(PayCenterResults.answered("0", PayCenterStatus.SUCCESS, null));
            when(supplementOrderMapper.updatePayStatusFromPending(eq(SUP_ORDER), eq("SUCCESS"), anyString())).thenReturn(1);
            when(supplementOrderMapper.selectItemsByOrderNo(SUP_ORDER)).thenReturn(List.of(item));
            when(gateTxnPayClient.convergeDebitStatusForSupplement(any(GateTxnPayDebitConvergeReqDTO.class)))
                    .thenReturn(convergeResp("0000", true, "SUCCESS"));

            flow.handlePayNotice(SUP_ORDER);

            InOrder inOrder = inOrder(supplementOrderMapper, gateTxnPayClient);
            inOrder.verify(supplementOrderMapper).updatePayStatusFromPending(eq(SUP_ORDER), eq("SUCCESS"), anyString());
            inOrder.verify(gateTxnPayClient).convergeDebitStatusForSupplement(any(GateTxnPayDebitConvergeReqDTO.class));
            inOrder.verify(supplementOrderMapper).updateItemSettleStatus(eq(SUP_ORDER), eq(ORIG_ORDER), eq("SETTLED"), any());
        }

        @Test
        void alreadySuccessIsIdempotent() {
            order.setPayStatus("SUCCESS");
            when(supplementOrderMapper.selectByOrderNo(SUP_ORDER))
                    .thenReturn(order)
                    .thenReturn(order);
            when(messageFactory.buildQueryRequest(SUP_ORDER)).thenReturn(queryRequest);
            when(payCenterClient.execute(any(), eq(queryRequest))).thenReturn(PayCenterResults.answered("0", PayCenterStatus.SUCCESS, null));
            when(supplementOrderMapper.updatePayStatusFromPending(eq(SUP_ORDER), eq("SUCCESS"), anyString())).thenReturn(0);

            flow.handlePayNotice(SUP_ORDER);

            verify(gateTxnPayClient, never()).convergeDebitStatusForSupplement(any());
        }

        @Test
        void convergeZeroRowsButAlreadySuccessMarksDuplicateRefund() {
            when(supplementOrderMapper.selectByOrderNo(SUP_ORDER)).thenReturn(order);
            when(messageFactory.buildQueryRequest(SUP_ORDER)).thenReturn(queryRequest);
            when(payCenterClient.execute(any(), eq(queryRequest))).thenReturn(PayCenterResults.answered("0", PayCenterStatus.SUCCESS, null));
            when(supplementOrderMapper.updatePayStatusFromPending(anyString(), eq("SUCCESS"), anyString())).thenReturn(1);
            when(supplementOrderMapper.selectItemsByOrderNo(SUP_ORDER)).thenReturn(List.of(item));
            when(gateTxnPayClient.convergeDebitStatusForSupplement(any(GateTxnPayDebitConvergeReqDTO.class)))
                    .thenReturn(convergeResp("0000", false, "SUCCESS"));

            flow.handlePayNotice(SUP_ORDER);

            verify(supplementOrderMapper).updateItemSettleStatus(
                    eq(SUP_ORDER), eq(ORIG_ORDER), eq("FAILED"), eq("重复支付待退款"));
        }

        @Test
        void convergeZeroRowsAndNotSuccessMarksFailed() {
            when(supplementOrderMapper.selectByOrderNo(SUP_ORDER)).thenReturn(order);
            when(messageFactory.buildQueryRequest(SUP_ORDER)).thenReturn(queryRequest);
            when(payCenterClient.execute(any(), eq(queryRequest))).thenReturn(PayCenterResults.answered("0", PayCenterStatus.SUCCESS, null));
            when(supplementOrderMapper.updatePayStatusFromPending(anyString(), eq("SUCCESS"), anyString())).thenReturn(1);
            when(supplementOrderMapper.selectItemsByOrderNo(SUP_ORDER)).thenReturn(List.of(item));
            when(gateTxnPayClient.convergeDebitStatusForSupplement(any(GateTxnPayDebitConvergeReqDTO.class)))
                    .thenReturn(convergeResp("8999", false, "INIT"));

            flow.handlePayNotice(SUP_ORDER);

            verify(supplementOrderMapper).updateItemSettleStatus(eq(SUP_ORDER), eq(ORIG_ORDER), eq("FAILED"), eq("原订单收敛失败"));
        }

        @Test
        void queryTransportFailureNeverTouchesStatus() {
            when(supplementOrderMapper.selectByOrderNo(SUP_ORDER)).thenReturn(order);
            when(messageFactory.buildQueryRequest(SUP_ORDER)).thenReturn(queryRequest);
            when(payCenterClient.execute(any(), eq(queryRequest))).thenReturn(PayCenterResults.transportFailed("connect refused"));

            flow.handlePayNotice(SUP_ORDER);

            verify(supplementOrderMapper, never()).updatePayStatusFromPending(anyString(), anyString(), anyString());
            verifyNoInteractions(gateTxnPayClient);
        }

        @Test
        void payCenterFailedMarksFailAndReleases() {
            when(supplementOrderMapper.selectByOrderNo(SUP_ORDER)).thenReturn(order);
            when(messageFactory.buildQueryRequest(SUP_ORDER)).thenReturn(queryRequest);
            when(payCenterClient.execute(any(), eq(queryRequest))).thenReturn(PayCenterResults.answered("0", PayCenterStatus.FAILED, "用户取消"));
            when(supplementOrderMapper.updatePayStatusFromPending(eq(SUP_ORDER), eq("FAIL"), anyString())).thenReturn(1);

            flow.handlePayNotice(SUP_ORDER);

            verify(supplementOrderMapper).updatePayStatusFromPending(eq(SUP_ORDER), eq("FAIL"), eq("支付中心返回失败: mock"));
        }

        @Test
        void nonTerminalStatusNeverUpdates() {
            when(supplementOrderMapper.selectByOrderNo(SUP_ORDER)).thenReturn(order);
            when(messageFactory.buildQueryRequest(SUP_ORDER)).thenReturn(queryRequest);
            when(payCenterClient.execute(any(), eq(queryRequest))).thenReturn(PayCenterResults.answered("0", PayCenterStatus.ORDERED, null));

            flow.handlePayNotice(SUP_ORDER);

            verify(supplementOrderMapper, never()).updatePayStatusFromPending(anyString(), anyString(), anyString());
            verifyNoInteractions(gateTxnPayClient);
        }
    }


    @Nested
    class ConvergePending {

        @Test
        void convergesSuccessOrders() {
            SupplementOrder po = new SupplementOrder();
            po.setOrderNo(SUP_ORDER);
            po.setPayStatus("PROCESSING");
            SupplementOrderItem item = new SupplementOrderItem();
            item.setOrderNo(SUP_ORDER);
            item.setOrigOrderNo(ORIG_ORDER);
            item.setOrigTxnDate(ORIG_TXN_DATE);

            when(supplementOrderMapper.selectPendingOrders(100, 24, 168)).thenReturn(List.of(po));
            when(messageFactory.buildQueryRequest(SUP_ORDER)).thenReturn(queryRequest);
            when(payCenterClient.execute(any(), eq(queryRequest))).thenReturn(PayCenterResults.answered("0", PayCenterStatus.SUCCESS, null));
            when(supplementOrderMapper.updatePayStatusFromPending(anyString(), eq("SUCCESS"), anyString())).thenReturn(1);
            when(supplementOrderMapper.selectItemsByOrderNo(SUP_ORDER)).thenReturn(List.of(item));
            when(gateTxnPayClient.convergeDebitStatusForSupplement(any(GateTxnPayDebitConvergeReqDTO.class)))
                    .thenReturn(convergeResp("0000", true, "SUCCESS"));

            int converged = flow.convergePending(100);

            assertEquals(1, converged);
        }

        @Test
        void unpaidClosesAndReleases() {
            SupplementOrder po = new SupplementOrder();
            po.setOrderNo(SUP_ORDER);
            po.setPayStatus("INIT");

            when(supplementOrderMapper.selectPendingOrders(100, 24, 168)).thenReturn(List.of(po));
            when(messageFactory.buildQueryRequest(SUP_ORDER)).thenReturn(queryRequest);
            when(payCenterClient.execute(any(), eq(queryRequest))).thenReturn(PayCenterResults.answered("0", PayCenterStatus.UNPAID, null));
            when(supplementOrderMapper.updatePayStatusFromPending(anyString(), eq("CLOSED"), anyString())).thenReturn(1);

            int converged = flow.convergePending(100);

            assertEquals(1, converged);
        }

        @Test
        void emptyScanReturnsZero() {
            when(supplementOrderMapper.selectPendingOrders(100, 24, 168)).thenReturn(List.of());

            int converged = flow.convergePending(100);

            assertEquals(0, converged);
            verifyNoInteractions(payCenterClient);
        }

        @Test
        void processingFreshOrderIsLeftAlone() {
            SupplementOrder po = new SupplementOrder();
            po.setOrderNo(SUP_ORDER);
            po.setPayStatus("PROCESSING");
            po.setUpdateTime(java.time.LocalDateTime.now());

            when(supplementOrderMapper.selectPendingOrders(100, 24, 168)).thenReturn(List.of(po));
            when(messageFactory.buildQueryRequest(SUP_ORDER)).thenReturn(queryRequest);
            when(payCenterClient.execute(any(), eq(queryRequest)))
                    .thenReturn(PayCenterResults.answered("0", PayCenterStatus.PROCESSING, null));

            int converged = flow.convergePending(100);

            assertEquals(0, converged);
            verify(supplementOrderMapper, never()).updatePayStatusFromPending(anyString(), anyString(), anyString());
        }

        @Test
        void processingStaleOrderIsClosed() {
            SupplementOrder po = new SupplementOrder();
            po.setOrderNo(SUP_ORDER);
            po.setPayStatus("PROCESSING");
            po.setUpdateTime(java.time.LocalDateTime.now().minusHours(25));

            when(supplementOrderMapper.selectPendingOrders(100, 24, 168)).thenReturn(List.of(po));
            when(messageFactory.buildQueryRequest(SUP_ORDER)).thenReturn(queryRequest);
            when(payCenterClient.execute(any(), eq(queryRequest)))
                    .thenReturn(PayCenterResults.answered("0", PayCenterStatus.PROCESSING, null));
            when(supplementOrderMapper.updatePayStatusFromPending(eq(SUP_ORDER), eq("CLOSED"), anyString()))
                    .thenReturn(1);

            int converged = flow.convergePending(100);

            assertEquals(1, converged);
            verify(supplementOrderMapper).updatePayStatusFromPending(
                    eq(SUP_ORDER), eq("CLOSED"), contains("PROCESSING"));
        }

        @Test
        void unrecognizedStatusIsLeftToTimeoutBackstop() {
            SupplementOrder po = new SupplementOrder();
            po.setOrderNo(SUP_ORDER);
            po.setPayStatus("PROCESSING");
            po.setUpdateTime(java.time.LocalDateTime.now().minusHours(25));

            when(supplementOrderMapper.selectPendingOrders(100, 24, 168)).thenReturn(List.of(po));
            when(messageFactory.buildQueryRequest(SUP_ORDER)).thenReturn(queryRequest);
            when(payCenterClient.execute(any(), eq(queryRequest)))
                    .thenReturn(PayCenterResults.answeredRaw("0", "SOME_NEW_STATUS", null));

            int converged = flow.convergePending(100);

            assertEquals(0, converged);
            verify(supplementOrderMapper, never()).updatePayStatusFromPending(anyString(), anyString(), anyString());
        }
    }
}
