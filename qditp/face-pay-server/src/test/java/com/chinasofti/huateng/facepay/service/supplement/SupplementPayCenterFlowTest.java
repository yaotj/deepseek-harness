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
import com.chinasofti.huateng.model.app.BlacklistAutoReleaseRespDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayDebitConvergeReqDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayDebitConvergeRespDTO;
import com.chinasofti.huateng.rpc.blacklist.BlacklistClient;
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
import static org.mockito.Mockito.doThrow;
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
    @Mock private BlacklistClient blacklistClient;
    @Mock private PayCenterProperties payCenterProperties;
    @Mock private PayCenterRequest payRequest;
    @Mock private PayCenterRequest queryRequest;

    private SupplementPayCenterFlow flow;

    @BeforeEach
    void setUp() {
        flow = new SupplementPayCenterFlow(payCenterClient, messageFactory, supplementOrderMapper,
                gateTxnPayClient, blacklistClient);
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

            var outcome = flow.preOrder(order, "WECHAT");

            assertInstanceOf(com.chinasofti.huateng.rpc.outcome.RpcOutcome.Ok.class, outcome);
            verify(supplementOrderMapper).updatePrepayResult(eq(SUP_ORDER), eq("WECHAT"), eq("PC20260914001"), eq("PAY_INFO_XXX"));
        }

        @Test
        void updatePrepayResultZeroRowsIsFailureNotIdempotentSuccess() {
            // 0 行 = 本次渠道与参数没落库，库里还是上一版渠道。此时 MUST 当失败：
            // 若当成功返回，调用方会拿库里的旧渠道参数回给乘客（2026-11-20 实测缺陷）。
            PayCenterResult result = PayCenterResults.answered("0", null, "PAY_INFO_XXX");
            when(messageFactory.buildPayRequest(any(), any())).thenReturn(payRequest);
            when(payCenterClient.execute(eq("http://paycenter/pay"), eq(payRequest))).thenReturn(result);
            when(supplementOrderMapper.updatePrepayResult(anyString(), anyString(), anyString(), anyString())).thenReturn(0);

            var outcome = flow.preOrder(order, "WECHAT");

            assertInstanceOf(com.chinasofti.huateng.rpc.outcome.RpcOutcome.BizRejected.class, outcome);
            assertEquals(SupplementPayCenterFlow.CODE_CHANNEL_NOT_APPLIED,
                    ((com.chinasofti.huateng.rpc.outcome.RpcOutcome.BizRejected) outcome).retCode());
        }

        @Test
        void transportFailureNeverTouchesDb() {
            when(messageFactory.buildPayRequest(any(), any())).thenReturn(payRequest);
            when(payCenterClient.execute(any(), eq(payRequest))).thenReturn(PayCenterResults.transportFailed("read timeout"));

            var outcome = flow.preOrder(order, "WECHAT");

            assertInstanceOf(com.chinasofti.huateng.rpc.outcome.RpcOutcome.Unreachable.class, outcome);
            verify(supplementOrderMapper, never()).updatePrepayResult(anyString(), anyString(), anyString(), anyString());
        }

        @Test
        void bizRejectedNeverUpdates() {
            PayCenterResult result = PayCenterResults.answered("9999", null, null);
            when(messageFactory.buildPayRequest(any(), any())).thenReturn(payRequest);
            when(payCenterClient.execute(any(), eq(payRequest))).thenReturn(result);

            var outcome = flow.preOrder(order, "WECHAT");

            assertInstanceOf(com.chinasofti.huateng.rpc.outcome.RpcOutcome.BizRejected.class, outcome);
            verify(supplementOrderMapper, never()).updatePrepayResult(anyString(), anyString(), anyString(), anyString());
        }

        @Test
        void channelNotHonoredIsRejectedAndNeverTouchesDb() {
            // 换渠道重下时支付中心仍回旧渠道（同一订单号下挂着旧待支付单）：MUST 拒绝且不写库，
            // 否则乘客在支付页看到的渠道与实际可付渠道不一致。
            PayCenterResult result = PayCenterResults.answeredWithChannel("0", "PAY_INFO_OLD", "UNIONPAY");
            when(messageFactory.buildPayRequest(any(), any())).thenReturn(payRequest);
            when(payCenterClient.execute(eq("http://paycenter/pay"), eq(payRequest))).thenReturn(result);

            var outcome = flow.preOrder(order, "WECHAT");

            assertInstanceOf(com.chinasofti.huateng.rpc.outcome.RpcOutcome.BizRejected.class, outcome);
            assertEquals(SupplementPayCenterFlow.CODE_CHANNEL_LOCKED,
                    ((com.chinasofti.huateng.rpc.outcome.RpcOutcome.BizRejected) outcome).retCode());
            verify(supplementOrderMapper, never()).updatePrepayResult(anyString(), anyString(), anyString(), anyString());
        }

        @Test
        void channelEchoMatchingRequestIsAccepted() {
            PayCenterResult result = PayCenterResults.answeredWithChannel("0", "PAY_INFO_WX", "WECHAT");
            when(messageFactory.buildPayRequest(any(), any())).thenReturn(payRequest);
            when(payCenterClient.execute(eq("http://paycenter/pay"), eq(payRequest))).thenReturn(result);
            when(supplementOrderMapper.updatePrepayResult(eq(SUP_ORDER), any(), anyString(), anyString())).thenReturn(1);

            var outcome = flow.preOrder(order, "WECHAT");

            assertInstanceOf(com.chinasofti.huateng.rpc.outcome.RpcOutcome.Ok.class, outcome);
            verify(supplementOrderMapper).updatePrepayResult(eq(SUP_ORDER), eq("WECHAT"), anyString(), eq("PAY_INFO_WX"));
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
        void settledOrderTriggersRealtimeBlacklistRelease() {
            order.setCardId("0426090949000508");
            when(supplementOrderMapper.selectByOrderNo(SUP_ORDER)).thenReturn(order);
            when(messageFactory.buildQueryRequest(SUP_ORDER)).thenReturn(queryRequest);
            when(payCenterClient.execute(any(), eq(queryRequest)))
                    .thenReturn(PayCenterResults.answered("0", PayCenterStatus.SUCCESS, null));
            when(supplementOrderMapper.updatePayStatusFromPending(eq(SUP_ORDER), eq("SUCCESS"), anyString())).thenReturn(1);
            when(supplementOrderMapper.selectItemsByOrderNo(SUP_ORDER)).thenReturn(List.of(item));
            when(gateTxnPayClient.convergeDebitStatusForSupplement(any(GateTxnPayDebitConvergeReqDTO.class)))
                    .thenReturn(convergeResp("0000", true, "SUCCESS"));
            BlacklistAutoReleaseRespDTO releaseResp = new BlacklistAutoReleaseRespDTO();
            releaseResp.setReleased(1);
            when(blacklistClient.releaseCardIfSettled(eq("0426090949000508"), any())).thenReturn(releaseResp);

            flow.handlePayNotice(SUP_ORDER);

            verify(blacklistClient).releaseCardIfSettled(eq("0426090949000508"), any());
        }

        @Test
        void blacklistReleaseFailureDoesNotBreakConverge() {
            order.setCardId("0426090949000508");
            when(supplementOrderMapper.selectByOrderNo(SUP_ORDER)).thenReturn(order);
            when(messageFactory.buildQueryRequest(SUP_ORDER)).thenReturn(queryRequest);
            when(payCenterClient.execute(any(), eq(queryRequest)))
                    .thenReturn(PayCenterResults.answered("0", PayCenterStatus.SUCCESS, null));
            when(supplementOrderMapper.updatePayStatusFromPending(eq(SUP_ORDER), eq("SUCCESS"), anyString())).thenReturn(1);
            when(supplementOrderMapper.selectItemsByOrderNo(SUP_ORDER)).thenReturn(List.of(item));
            when(gateTxnPayClient.convergeDebitStatusForSupplement(any(GateTxnPayDebitConvergeReqDTO.class)))
                    .thenReturn(convergeResp("0000", true, "SUCCESS"));
            doThrow(new RuntimeException("blacklist down"))
                    .when(blacklistClient).releaseCardIfSettled(eq("0426090949000508"), any());

            // 解黑 RPC 抛异常也不能把已成功的补款收敛变成异常（兜底交给每日全表解黑任务）。
            flow.handlePayNotice(SUP_ORDER);

            verify(supplementOrderMapper).updateItemSettleStatus(eq(SUP_ORDER), eq(ORIG_ORDER), eq("SETTLED"), any());
        }

        @Test
        void noCardIdSkipsRealtimeRelease() {
            // order 未设 cardId，收敛成功但不应触发解黑（cardId 为空）。
            when(supplementOrderMapper.selectByOrderNo(SUP_ORDER)).thenReturn(order);
            when(messageFactory.buildQueryRequest(SUP_ORDER)).thenReturn(queryRequest);
            when(payCenterClient.execute(any(), eq(queryRequest)))
                    .thenReturn(PayCenterResults.answered("0", PayCenterStatus.SUCCESS, null));
            when(supplementOrderMapper.updatePayStatusFromPending(eq(SUP_ORDER), eq("SUCCESS"), anyString())).thenReturn(1);
            when(supplementOrderMapper.selectItemsByOrderNo(SUP_ORDER)).thenReturn(List.of(item));
            when(gateTxnPayClient.convergeDebitStatusForSupplement(any(GateTxnPayDebitConvergeReqDTO.class)))
                    .thenReturn(convergeResp("0000", true, "SUCCESS"));

            flow.handlePayNotice(SUP_ORDER);

            verifyNoInteractions(blacklistClient);
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
