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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SupplementOrderServiceImplTest {

    private static final String ORIG_1 = "GTP202609150001";
    private static final String ORIG_2 = "GTP202609150002";
    private static final String USER_ID = "U1234567890";
    private static final String CARD_ID = "0000000000CARD01";
    private static final String CARD_ID_SHORT = "CARD01";
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


    @Nested
    class ValidateOnly {

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

        @Test
        void overLimitRejected() {
            SupplementOrderReqDTO req = new SupplementOrderReqDTO();
            req.setOrderNoList(List.of());

            java.util.ArrayList<String> big = new java.util.ArrayList<>();
            for (int i = 0; i < 501; i++) {
                big.add("ORDER" + i);
            }
            req.setOrderNoList(big);

            SupplementOrderRespDTO resp = service.requestPayOrder(req);

            assertEquals("8001", resp.getRetCode());
            verify(gateTxnPayMapper, never()).selectByOrderNos(anyList());
        }

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


    @Nested
    class HappyPath {

        @Test
        void acceptedReturnsProcessing() {
            SupplementOrderReqDTO req = validReq();
            setupOrigOrders();
            when(localWriter.persist(any(), anyList(), any())).thenReturn(SupplementOrderLocalWriter.PersistResult.ok("SP202609150001"));
            when(payCenterFlow.preOrder(any())).thenReturn(new com.chinasofti.huateng.rpc.outcome.RpcOutcome.Ok());

            SupplementOrderRespDTO resp = service.requestPayOrder(req);

            assertEquals("0000", resp.getRetCode());
            assertEquals("PROCESSING", resp.getPayStatus());
            assertNotNull(resp.getOrderNo());
            assertEquals("SP", resp.getOrderNo().substring(0, 2));
        }

        @Test
        void unreachableReturnsInit() {
            SupplementOrderReqDTO req = validReq();
            setupOrigOrders();
            when(localWriter.persist(any(), anyList(), any())).thenReturn(SupplementOrderLocalWriter.PersistResult.ok("SP202609150001"));
            when(payCenterFlow.preOrder(any())).thenReturn(new com.chinasofti.huateng.rpc.outcome.RpcOutcome.Unreachable(new RuntimeException("timeout")));

            SupplementOrderRespDTO resp = service.requestPayOrder(req);

            assertEquals("0000", resp.getRetCode());
            assertEquals("INIT", resp.getPayStatus());
            assertNotNull(resp.getOrderNo());
        }

        @Test
        void bizRejectedReturnsInit() {
            SupplementOrderReqDTO req = validReq();
            setupOrigOrders();
            when(localWriter.persist(any(), anyList(), any())).thenReturn(SupplementOrderLocalWriter.PersistResult.ok("SP202609150001"));
            when(payCenterFlow.preOrder(any())).thenReturn(new com.chinasofti.huateng.rpc.outcome.RpcOutcome.BizRejected("9999", "商户号异常"));
            when(supplementOrderMapper.updatePayStatusFromPending(anyString(), eq("INIT"), anyString())).thenReturn(1);

            SupplementOrderRespDTO resp = service.requestPayOrder(req);

            assertEquals("8003", resp.getRetCode());
            assertEquals("INIT", resp.getPayStatus());
        }

        @Test
        void persistRejectedReturns8003() {
            SupplementOrderReqDTO req = validReq();
            setupOrigOrders();
            when(localWriter.persist(any(), anyList(), any())).thenReturn(SupplementOrderLocalWriter.PersistResult.rejected("SP202609150001"));

            SupplementOrderRespDTO resp = service.requestPayOrder(req);

            assertEquals("8003", resp.getRetCode());
            assertEquals("补款单已存在，请勿重复提交", resp.getRetMsg());
            verify(payCenterFlow, never()).preOrder(any());
        }

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


    @Nested
    class Guards {

        @Test
        void identifiesSupplementOrder() {
            when(supplementOrderMapper.selectByOrderNo("SP202609150001")).thenReturn(new SupplementOrder());
            when(supplementOrderMapper.selectByOrderNo("GTP202609150001")).thenReturn(null);

            assertEquals(true, service.isSupplementOrder("SP202609150001"));
            assertEquals(false, service.isSupplementOrder("GTP202609150001"));
        }

        @Test
        void isSupplementOrderDbExceptionDegradesFalse() {
            when(supplementOrderMapper.selectByOrderNo(anyString())).thenThrow(new RuntimeException("DB down"));

            assertEquals(false, service.isSupplementOrder("SP202609150001"));
        }
    }


    @Nested
    class CloseTimeout {

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

        @Test
        void closesStaleProcessing() {
            SupplementOrder order = new SupplementOrder();
            order.setOrderNo("SP_STALE_001");
            order.setPayStatus("PROCESSING");
            when(supplementOrderMapper.selectTimeoutProcessing(1440, 100)).thenReturn(List.of(order));
            when(supplementOrderMapper.closeStaleProcessingOrder(eq("SP_STALE_001"), anyString())).thenReturn(1);

            int closed = service.closeStaleProcessingOrders(1440, 100);

            assertEquals(1, closed);
            verify(supplementOrderMapper).closeStaleProcessingOrder(eq("SP_STALE_001"), anyString());
        }
    }


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
                gateTxnPay(ORIG_2, "RETRY")
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
