package com.chinasofti.huateng.facepay.service.supplement;

import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterClient;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterMessageFactory;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterProperties;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterRequest;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterResult;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterResults;
import com.chinasofti.huateng.facepay.entity.GateTxnPay;
import com.chinasofti.huateng.facepay.entity.SupplementOrder;
import com.chinasofti.huateng.facepay.mapper.GateTxnPayMapper;
import com.chinasofti.huateng.facepay.mapper.SupplementOrderMapper;
import com.chinasofti.huateng.model.pay.SupplementOrderReqDTO;
import com.chinasofti.huateng.model.pay.SupplementOrderRespDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 锁死 {@link SupplementOrderServiceImpl} 的编排语义。
 *
 * <p>验证范围：从 APP 下单请求进来 → 原订单校验 → 作废旧单 → 本地落单 → PayCenter 预下单 → 返回的完整编排链路。</p>
 *
 * <p>⚠️ 注意：本测试直接 {@code new SupplementOrderServiceImpl(...)} 而不是 {@code @InjectMocks}，
 * 因为 Mockito 3.x 对 final 类的 mock maker 有已知限制（PayCenterResult 是 final），
 * 构造器注入更明确地表达依赖图。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SupplementOrderServiceImplTest {

    private static final String ORIG_1 = "GTP202609150001";
    private static final String ORIG_2 = "GTP202609150002";
    private static final String USER_ID = "U1234567890";
    private static final String CARD_ID = "0000000000CARD01";
    private static final String CARD_ID_SHORT = "CARD01"; // 后6位，用于补款单号后缀
    private static final String VENDOR = "WECHAT";
    private static final String SIGN_CHANNEL = "WECHAT_FACE";
    private static final String TXN_DATE = "20260915";

    @Mock private SupplementOrderMapper supplementOrderMapper;
    @Mock private GateTxnPayMapper gateTxnPayMapper;
    @Mock private SupplementPayCenterFlow payCenterFlow;
    @Mock private SupplementOrderLocalWriter localWriter;

    private SupplementOrderServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SupplementOrderServiceImpl(supplementOrderMapper, gateTxnPayMapper, payCenterFlow, localWriter);
    }

    // ==================== requestPayOrder 校验分支 ====================

    @Nested
    class ValidateOnly {

        /** orderNoList 空 → 直接返回 8001。NEVER 查 DB。 */
        @Test
        void emptyListRejected() {
            SupplementOrderReqDTO req = new SupplementOrderReqDTO();
            req.setOrderNoList(List.of());

            SupplementOrderRespDTO resp = service.requestPayOrder(req);

            assertEquals("8001", resp.getRetCode());
            verifyNoInteractions(gateTxnPayMapper);
            verifyNoInteractions(localWriter);
            verifyNoInteractions(payCenterFlow);
        }

        /** orderNoList 超过 500 → 直接返回 8001。 */
        @Test
        void overLimitRejected() {
            SupplementOrderReqDTO req = new SupplementOrderReqDTO();
            req.setOrderNoList(List.of()); // 实际要构造 501 个，简化

            // 构造 501 个
            java.util.ArrayList<String> big = new java.util.ArrayList<>();
            for (int i = 0; i < 501; i++) {
                big.add("ORDER" + i);
            }
            req.setOrderNoList(big);

            SupplementOrderRespDTO resp = service.requestPayOrder(req);

            assertEquals("8001", resp.getRetCode());
            verify(gateTxnPayMapper, never()).selectByOrderNos(anyList());
        }

        /** 部分原订单不存在 → 8001。 */
        @Test
        void missingOrigOrdersRejected() {
            SupplementOrderReqDTO req = validReq();
            when(gateTxnPayMapper.selectByOrderNos(List.of(ORIG_1, ORIG_2))).thenReturn(List.of(
                    gateTxnPay(ORIG_1, "INIT")
            ));

            SupplementOrderRespDTO resp = service.requestPayOrder(req);

            assertEquals("8001", resp.getRetCode());
            assertEquals("存在无效的原订单号", resp.getRetMsg());
        }

        /** 原订单已结清（SUCCESS）→ 8003。 */
        @Test
        void settledOrigOrdersRejected() {
            SupplementOrderReqDTO req = validReq();
            when(gateTxnPayMapper.selectByOrderNos(List.of(ORIG_1, ORIG_2))).thenReturn(List.of(
                    gateTxnPay(ORIG_1, "INIT"),
                    gateTxnPay(ORIG_2, "SUCCESS")
            ));

            SupplementOrderRespDTO resp = service.requestPayOrder(req);

            assertEquals("8003", resp.getRetCode());
            assertEquals("部分订单已结清或状态不可补款", resp.getRetMsg());
        }

        /** 原订单归属不一致（不同 cardId）→ 8001。 */
        @Test
        void ownerMismatchRejected() {
            SupplementOrderReqDTO req = validReq();
            when(gateTxnPayMapper.selectByOrderNos(List.of(ORIG_1, ORIG_2))).thenReturn(List.of(
                    gateTxnPayWithCard(ORIG_1, "INIT", "CARD_A"),
                    gateTxnPayWithCard(ORIG_2, "INIT", "CARD_B")
            ));

            SupplementOrderRespDTO resp = service.requestPayOrder(req);

            assertEquals("8001", resp.getRetCode());
            assertEquals("订单归属不一致", resp.getRetMsg());
        }

        /** thirdUserId 与原订单不一致 → 8001。 */
        @Test
        void thirdUserIdMismatchRejected() {
            SupplementOrderReqDTO req = validReq();
            req.setThirdUserId("OTHER_USER");
            when(gateTxnPayMapper.selectByOrderNos(List.of(ORIG_1, ORIG_2))).thenReturn(List.of(
                    gateTxnPay(ORIG_1, "INIT"),
                    gateTxnPay(ORIG_2, "INIT")
            ));

            SupplementOrderRespDTO resp = service.requestPayOrder(req);

            assertEquals("8001", resp.getRetCode());
        }
    }

    // ==================== requestPayOrder 成功链路 ====================

    @Nested
    class HappyPath {

        /** PayCenter 受理成功 → 补款单 PROCESSING。 */
        @Test
        void acceptedReturnsProcessing() {
            SupplementOrderReqDTO req = validReq();
            setupOrigOrders();
            // 本地落单成功
            when(localWriter.persist(any(), anyList(), any())).thenReturn(SupplementOrderLocalWriter.PersistResult.ok("SP202609150001"));
            // PayCenter 受理成功
            when(payCenterFlow.preOrder(any())).thenReturn(new com.chinasofti.huateng.rpc.outcome.RpcOutcome.Ok());

            SupplementOrderRespDTO resp = service.requestPayOrder(req);

            assertEquals("0000", resp.getRetCode());
            assertEquals("PROCESSING", resp.getPayStatus());
            assertNotNull(resp.getOrderNo());
            // 补款单号 MUST 以 SP 开头
            assertEquals("SP", resp.getOrderNo().substring(0, 2));
        }

        /** PayCenter 不可达 → 补款单 INIT（先落单再预下单，不可达不回滚本地）。 */
        @Test
        void unreachableReturnsInit() {
            SupplementOrderReqDTO req = validReq();
            setupOrigOrders();
            when(localWriter.persist(any(), anyList(), any())).thenReturn(SupplementOrderLocalWriter.PersistResult.ok("SP202609150001"));
            when(payCenterFlow.preOrder(any())).thenReturn(new com.chinasofti.huateng.rpc.outcome.RpcOutcome.Unreachable(new RuntimeException("timeout")));

            SupplementOrderRespDTO resp = service.requestPayOrder(req);

            // 不可达视为"补款单已生成，待支付中心受理"
            assertEquals("0000", resp.getRetCode());
            assertEquals("INIT", resp.getPayStatus());
            assertNotNull(resp.getOrderNo());
        }

        /** PayCenter BizRejected → 补款单回 INIT + 8003。 */
        @Test
        void bizRejectedReturnsInit() {
            SupplementOrderReqDTO req = validReq();
            setupOrigOrders();
            when(localWriter.persist(any(), anyList(), any())).thenReturn(SupplementOrderLocalWriter.PersistResult.ok("SP202609150001"));
            when(payCenterFlow.preOrder(any())).thenReturn(new com.chinasofti.huateng.rpc.outcome.RpcOutcome.BizRejected("9999", "商户号异常"));
            // BizRejected 会回写 INIT
            when(supplementOrderMapper.updatePayStatusFromPending(anyString(), eq("INIT"), anyString())).thenReturn(1);

            SupplementOrderRespDTO resp = service.requestPayOrder(req);

            assertEquals("8003", resp.getRetCode());
            assertEquals("INIT", resp.getPayStatus());
        }

        /**
         * 本地落单撞主表唯一索引（重复下单）→ PersistResult.rejected → 8003。
         */
        @Test
        void persistRejectedReturns8003() {
            SupplementOrderReqDTO req = validReq();
            setupOrigOrders();
            // 撞唯一索引，视为被拒绝
            when(localWriter.persist(any(), anyList(), any())).thenReturn(SupplementOrderLocalWriter.PersistResult.rejected("SP202609150001"));

            SupplementOrderRespDTO resp = service.requestPayOrder(req);

            assertEquals("8003", resp.getRetCode());
            assertEquals("补款单已存在，请勿重复提交", resp.getRetMsg());
            // NEVER 调 preOrder —— 本地没落到，就不该往 PayCenter 发请求
            verify(payCenterFlow, never()).preOrder(any());
        }

        /** 预下单抛异常 → 回 INIT + 8001。 */
        @Test
        void preOrderExceptionReturnsInit() {
            SupplementOrderReqDTO req = validReq();
            setupOrigOrders();
            when(localWriter.persist(any(), anyList(), any())).thenReturn(SupplementOrderLocalWriter.PersistResult.ok("SP202609150001"));
            when(payCenterFlow.preOrder(any())).thenThrow(new RuntimeException("connection refused"));
            when(supplementOrderMapper.updatePayStatusFromPending(anyString(), eq("INIT"), anyString())).thenReturn(1);

            SupplementOrderRespDTO resp = service.requestPayOrder(req);

            assertEquals("8001", resp.getRetCode());
            assertEquals("INIT", resp.getPayStatus());
        }
    }

    // ==================== isSupplementOrder ====================

    @Nested
    class Guards {

        /** 补款单表里能查到 → true。 */
        @Test
        void identifiesSupplementOrder() {
            when(supplementOrderMapper.selectByOrderNo("SP202609150001")).thenReturn(new SupplementOrder());
            when(supplementOrderMapper.selectByOrderNo("GTP202609150001")).thenReturn(null);

            assertEquals(true, service.isSupplementOrder("SP202609150001"));
            assertEquals(false, service.isSupplementOrder("GTP202609150001"));
        }

        /** isSupplementOrder DB 异常 → 降级 false（保守：不要把非补款单误判成补款单）。 */
        @Test
        void isSupplementOrderDbExceptionDegradesFalse() {
            when(supplementOrderMapper.selectByOrderNo(anyString())).thenThrow(new RuntimeException("DB down"));

            assertEquals(false, service.isSupplementOrder("SP202609150001"));
        }
    }

    // ==================== closeTimeoutOrders ====================

    @Nested
    class CloseTimeout {

        /** 超时关单。 */
        @Test
        void closesTimeoutAndReleases() {
            SupplementOrder order = new SupplementOrder();
            order.setOrderNo("SP_TIMEOUT_001");
            order.setPayStatus("INIT");
            when(supplementOrderMapper.selectTimeoutPending(30, 100)).thenReturn(List.of(order));
            when(supplementOrderMapper.closeTimeoutOrder(eq("SP_TIMEOUT_001"), anyString())).thenReturn(1);

            int closed = service.closeTimeoutOrders(30, 100);

            assertEquals(1, closed);
            verify(supplementOrderMapper).closeTimeoutOrder(eq("SP_TIMEOUT_001"), anyString());
        }
    }

    // ==================== helpers ====================

    private SupplementOrderReqDTO validReq() {
        SupplementOrderReqDTO req = new SupplementOrderReqDTO();
        req.setOrderNoList(List.of(ORIG_1, ORIG_2));
        req.setThirdUserId(USER_ID);
        req.setCardId(CARD_ID);
        req.setGoodsCode("001");
        return req;
    }

    private void setupOrigOrders() {
        when(gateTxnPayMapper.selectByOrderNos(List.of(ORIG_1, ORIG_2))).thenReturn(List.of(
                gateTxnPay(ORIG_1, "INIT"),
                gateTxnPay(ORIG_2, "RETRY") // RETRY 也在待补款白名单里
        ));
    }

    private GateTxnPay gateTxnPay(String orderNo, String debitStatus) {
        return gateTxnPayWithCard(orderNo, debitStatus, CARD_ID);
    }

    private GateTxnPay gateTxnPayWithCard(String orderNo, String debitStatus, String cardId) {
        GateTxnPay p = new GateTxnPay();
        p.setOrderNo(orderNo);
        p.setDebitStatus(debitStatus);
        p.setThirdUserId(USER_ID);
        p.setCardId(cardId);
        p.setCardType("CARD_A");
        p.setTxnDate(TXN_DATE);
        p.setTotalAmount(400);
        p.setPaymentVendor(VENDOR);
        p.setSignChannelCode(SIGN_CHANNEL);
        return p;
    }
}
