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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 锁死 {@link SupplementPayCenterFlow} 的收口语义。 */
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

    /** 造一条收敛响应。 */
    private static GateTxnPayDebitConvergeRespDTO convergeResp(String retCode, Boolean converged,
                                                               String debitStatus) {
        GateTxnPayDebitConvergeRespDTO resp = new GateTxnPayDebitConvergeRespDTO();
        resp.setRetCode(retCode);
        resp.setOrigOrderNo(ORIG_ORDER);
        resp.setConverged(converged);
        resp.setDebitStatus(debitStatus);
        return resp;
    }

    // ==================== preOrder ====================

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

        /** PayCenter 受理成功 → UPDATE 推 PROCESSING + 渠道凭据。 */
        @Test
        void acceptedUpdatesPrepayResult() {
            PayCenterResult result = PayCenterResults.answered("0", null, "PAY_INFO_XXX");
            when(messageFactory.buildPayRequest(any(), any())).thenReturn(payRequest);
            when(payCenterClient.execute(eq("http://paycenter/pay"), eq(payRequest))).thenReturn(result);
            when(supplementOrderMapper.updatePrepayResult(eq(SUP_ORDER), any(), anyString(), anyString())).thenReturn(1);

            var outcome = flow.preOrder(order);

            assertInstanceOf(com.chinasofti.huateng.rpc.outcome.RpcOutcome.Ok.class, outcome);
            // PayCenterResults.answered 的 body 没有 "payChannelCode" key → 生产 PayCenter 会返回，测试里为 null
            verify(supplementOrderMapper).updatePrepayResult(eq(SUP_ORDER), org.mockito.ArgumentMatchers.isNull(), eq("PC20260914001"), eq("PAY_INFO_XXX"));
        }

        /** UPDATE 返回 0 行时视为幂等（已 PROCESSING），NEVER 报错。 */
        @Test
        void updatePrepayResultZeroRowsIsIdempotent() {
            PayCenterResult result = PayCenterResults.answered("0", null, "PAY_INFO_XXX");
            when(messageFactory.buildPayRequest(any(), any())).thenReturn(payRequest);
            when(payCenterClient.execute(eq("http://paycenter/pay"), eq(payRequest))).thenReturn(result);
            // 第二次 update 自然返回 0（已经 PROCESSING 了）
            when(supplementOrderMapper.updatePrepayResult(anyString(), anyString(), anyString(), anyString())).thenReturn(0);

            var outcome = flow.preOrder(order);

            assertInstanceOf(com.chinasofti.huateng.rpc.outcome.RpcOutcome.Ok.class, outcome);
        }

        /** 对端传输失败 → Unreachable。 */
        @Test
        void transportFailureNeverTouchesDb() {
            when(messageFactory.buildPayRequest(any(), any())).thenReturn(payRequest);
            when(payCenterClient.execute(any(), eq(payRequest))).thenReturn(PayCenterResults.transportFailed("read timeout"));

            var outcome = flow.preOrder(order);

            assertInstanceOf(com.chinasofti.huateng.rpc.outcome.RpcOutcome.Unreachable.class, outcome);
            verify(supplementOrderMapper, never()).updatePrepayResult(anyString(), anyString(), anyString(), anyString());
        }

        /** 对端返回 code!=0 → BizRejected。 */
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

    // ==================== handlePayNotice ====================

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

        /** 补款单不存在 → 静默返回，NEVER 报错。 */
        @Test
        void missingOrderIsSilentlyIgnored() {
            when(supplementOrderMapper.selectByOrderNo(SUP_ORDER)).thenReturn(null);

            flow.handlePayNotice(SUP_ORDER);

            verifyNoInteractions(payCenterClient);
            verifyNoInteractions(gateTxnPayClient);
        }

        /** 回调 SUCCESS → 收敛链完整：推补款单 SUCCESS → RPC 收敛原订单 → 更新明细 SETTLED。 */
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

        /** 回查时补款单已是 SUCCESS → 幂等跳过收敛链。 */
        @Test
        void alreadySuccessIsIdempotent() {
            order.setPayStatus("SUCCESS");
            when(supplementOrderMapper.selectByOrderNo(SUP_ORDER))
                    .thenReturn(order)         // 第一次 updatePayStatusFromPending 返回 0 后回查
                    .thenReturn(order);        // 第二次 selectByOrderNo（看最新状态）
            when(messageFactory.buildQueryRequest(SUP_ORDER)).thenReturn(queryRequest);
            when(payCenterClient.execute(any(), eq(queryRequest))).thenReturn(PayCenterResults.answered("0", PayCenterStatus.SUCCESS, null));
            // updatePayStatusFromPending 白名单只放 INIT/PROCESSING，已 SUCCESS 返回 0
            when(supplementOrderMapper.updatePayStatusFromPending(eq(SUP_ORDER), eq("SUCCESS"), anyString())).thenReturn(0);

            flow.handlePayNotice(SUP_ORDER);

            verify(gateTxnPayClient, never()).convergeDebitStatusForSupplement(any());
        }

        /** 收敛返回 {@code converged=false} 但原订单已是 SUCCESS → 无独占下系后到补款单重复支付， */
        @Test
        void convergeZeroRowsButAlreadySuccessMarksDuplicateRefund() {
            when(supplementOrderMapper.selectByOrderNo(SUP_ORDER)).thenReturn(order);
            when(messageFactory.buildQueryRequest(SUP_ORDER)).thenReturn(queryRequest);
            when(payCenterClient.execute(any(), eq(queryRequest))).thenReturn(PayCenterResults.answered("0", PayCenterStatus.SUCCESS, null));
            when(supplementOrderMapper.updatePayStatusFromPending(anyString(), eq("SUCCESS"), anyString())).thenReturn(1);
            when(supplementOrderMapper.selectItemsByOrderNo(SUP_ORDER)).thenReturn(List.of(item));
            // 原订单已在 SUCCESS（converge 白名单外）——被先到补款单抢先结清
            when(gateTxnPayClient.convergeDebitStatusForSupplement(any(GateTxnPayDebitConvergeReqDTO.class)))
                    .thenReturn(convergeResp("0000", false, "SUCCESS"));

            flow.handlePayNotice(SUP_ORDER);

            verify(supplementOrderMapper).updateItemSettleStatus(
                    eq(SUP_ORDER), eq(ORIG_ORDER), eq("FAILED"), eq("重复支付待退款"));
        }

        /** 收敛返回 {@code converged=false} 且原订单仍非 SUCCESS → 明细标 FAILED 需人工核对。 */
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

        /** 回查 PayCenter 传输失败 → NEVER 推状态，也 NEVER 收敛原订单。 */
        @Test
        void queryTransportFailureNeverTouchesStatus() {
            when(supplementOrderMapper.selectByOrderNo(SUP_ORDER)).thenReturn(order);
            when(messageFactory.buildQueryRequest(SUP_ORDER)).thenReturn(queryRequest);
            when(payCenterClient.execute(any(), eq(queryRequest))).thenReturn(PayCenterResults.transportFailed("connect refused"));

            flow.handlePayNotice(SUP_ORDER);

            verify(supplementOrderMapper, never()).updatePayStatusFromPending(anyString(), anyString(), anyString());
            verifyNoInteractions(gateTxnPayClient);
        }

        /** PayCenter 返回失败（非 transport 错误）→ 补款单推 FAIL + 释放独占。 */
        @Test
        void payCenterFailedMarksFailAndReleases() {
            when(supplementOrderMapper.selectByOrderNo(SUP_ORDER)).thenReturn(order);
            when(messageFactory.buildQueryRequest(SUP_ORDER)).thenReturn(queryRequest);
            when(payCenterClient.execute(any(), eq(queryRequest))).thenReturn(PayCenterResults.answered("0", PayCenterStatus.FAILED, "用户取消"));
            when(supplementOrderMapper.updatePayStatusFromPending(eq(SUP_ORDER), eq("FAIL"), anyString())).thenReturn(1);

            flow.handlePayNotice(SUP_ORDER);

            // PayCenterResults.answered 的 msg 参数是 "mock"（第 2 个参数），不是传入的 "用户取消"（那是 data）
            verify(supplementOrderMapper).updatePayStatusFromPending(eq(SUP_ORDER), eq("FAIL"), eq("支付中心返回失败: mock"));
        }

        /** PayCenter 返回非终态（ORDERED 已下单待支付）→ 不动状态。 */
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

    // ==================== convergePending ====================

    @Nested
    class ConvergePending {

        /** 定时收敛扫描 → 逐条回查 PayCenter SUCCESS → 完整收敛链。 */
        @Test
        void convergesSuccessOrders() {
            SupplementOrder po = new SupplementOrder();
            po.setOrderNo(SUP_ORDER);
            po.setPayStatus("PROCESSING");
            SupplementOrderItem item = new SupplementOrderItem();
            item.setOrderNo(SUP_ORDER);
            item.setOrigOrderNo(ORIG_ORDER);
            item.setOrigTxnDate(ORIG_TXN_DATE);

            when(supplementOrderMapper.selectPendingOrders(100, 24)).thenReturn(List.of(po));
            when(messageFactory.buildQueryRequest(SUP_ORDER)).thenReturn(queryRequest);
            when(payCenterClient.execute(any(), eq(queryRequest))).thenReturn(PayCenterResults.answered("0", PayCenterStatus.SUCCESS, null));
            when(supplementOrderMapper.updatePayStatusFromPending(anyString(), eq("SUCCESS"), anyString())).thenReturn(1);
            when(supplementOrderMapper.selectItemsByOrderNo(SUP_ORDER)).thenReturn(List.of(item));
            when(gateTxnPayClient.convergeDebitStatusForSupplement(any(GateTxnPayDebitConvergeReqDTO.class)))
                    .thenReturn(convergeResp("0000", true, "SUCCESS"));

            int converged = flow.convergePending(100);

            assertEquals(1, converged);
        }

        /** 回查 UNPAID → 关单。 */
        @Test
        void unpaidClosesAndReleases() {
            SupplementOrder po = new SupplementOrder();
            po.setOrderNo(SUP_ORDER);
            po.setPayStatus("INIT");

            when(supplementOrderMapper.selectPendingOrders(100, 24)).thenReturn(List.of(po));
            when(messageFactory.buildQueryRequest(SUP_ORDER)).thenReturn(queryRequest);
            when(payCenterClient.execute(any(), eq(queryRequest))).thenReturn(PayCenterResults.answered("0", PayCenterStatus.UNPAID, null));
            when(supplementOrderMapper.updatePayStatusFromPending(anyString(), eq("CLOSED"), anyString())).thenReturn(1);

            int converged = flow.convergePending(100);

            assertEquals(1, converged);
        }

        /** 扫描空 → 收敛 0 条。 */
        @Test
        void emptyScanReturnsZero() {
            when(supplementOrderMapper.selectPendingOrders(100, 24)).thenReturn(List.of());

            int converged = flow.convergePending(100);

            assertEquals(0, converged);
            verifyNoInteractions(payCenterClient);
        }
    }
}
