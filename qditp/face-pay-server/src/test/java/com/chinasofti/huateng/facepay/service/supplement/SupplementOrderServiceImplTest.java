package com.chinasofti.huateng.facepay.service.supplement;

import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterClient;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterMessageFactory;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterProperties;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterRequest;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterResult;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterResults;
import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.facepay.api.device.app.RequestPayInfoReqDTO;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
                    gateTxnPay(ORIG_1, "RETRY")
            ));

            SupplementOrderRespDTO resp = service.requestPayOrder(req);

            assertEquals("8001", resp.getRetCode());
            assertEquals("存在无效的原订单号", resp.getRetMsg());
        }

        @Test
        void settledOrigOrdersRejected() {
            SupplementOrderReqDTO req = validReq();
            when(gateTxnPayMapper.selectByOrderNos(List.of(ORIG_1, ORIG_2))).thenReturn(List.of(
                    gateTxnPay(ORIG_1, "RETRY"),
                    gateTxnPay(ORIG_2, "SUCCESS")
            ));

            SupplementOrderRespDTO resp = service.requestPayOrder(req);

            assertEquals("8003", resp.getRetCode());
            assertEquals("部分订单已结清或正在扣费中，暂不可补款", resp.getRetMsg());
        }

        /**
         * 2026-11-20 用户裁决：INIT（已落单、正在发往支付中心）不可补款 —— 发起中窗口里钱可能已在路上。
         * 卡 INIT 的单由 DebitRetryProcessor 的定时扫描接管，不靠补款自救。
         */
        @Test
        void initDebitOrderIsNotSupplementable() {
            SupplementOrderReqDTO req = validReq();
            when(gateTxnPayMapper.selectByOrderNos(List.of(ORIG_1, ORIG_2))).thenReturn(List.of(
                    gateTxnPay(ORIG_1, "INIT"),
                    gateTxnPay(ORIG_2, "FAIL")
            ));

            SupplementOrderRespDTO resp = service.requestPayOrder(req);

            assertEquals("8003", resp.getRetCode());
            assertEquals("部分订单已结清或正在扣费中，暂不可补款", resp.getRetMsg());
            verifyLocalWriterNeverCalled();
        }

        /** PROCESSING 同样不可补款：已发往支付中心、正在等扣款结果。 */
        @Test
        void processingDebitOrderIsNotSupplementable() {
            SupplementOrderReqDTO req = validReq();
            when(gateTxnPayMapper.selectByOrderNos(List.of(ORIG_1, ORIG_2))).thenReturn(List.of(
                    gateTxnPay(ORIG_1, "FAIL"),
                    gateTxnPay(ORIG_2, "PROCESSING")
            ));

            SupplementOrderRespDTO resp = service.requestPayOrder(req);

            assertEquals("8003", resp.getRetCode());
            verifyLocalWriterNeverCalled();
        }

        private void verifyLocalWriterNeverCalled() {
            verify(localWriter, never()).persist(any(), anyList(), any());
        }

        @Test
        void ownerMismatchRejected() {
            SupplementOrderReqDTO req = validReq();
            when(gateTxnPayMapper.selectByOrderNos(List.of(ORIG_1, ORIG_2))).thenReturn(List.of(
                    gateTxnPayWithCard(ORIG_1, "RETRY", "CARD_A"),
                    gateTxnPayWithCard(ORIG_2, "RETRY", "CARD_B")
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
                    gateTxnPay(ORIG_1, "RETRY"),
                    gateTxnPay(ORIG_2, "RETRY")
            ));

            SupplementOrderRespDTO resp = service.requestPayOrder(req);

            assertEquals("8001", resp.getRetCode());
        }
    }


    @Nested
    class HappyPath {

        @Test
        void createsOrderAsInitWithoutPrepay() {
            SupplementOrderReqDTO req = validReq();
            setupOrigOrders();
            when(localWriter.persist(any(), anyList(), any())).thenReturn("SP202609150001");

            SupplementOrderRespDTO resp = service.requestPayOrder(req);

            assertEquals("0000", resp.getRetCode());
            assertEquals("INIT", resp.getPayStatus());
            assertNotNull(resp.getOrderNo());
            assertEquals("SP", resp.getOrderNo().substring(0, 2));
            // IF8A-26 报文没有渠道字段，预下单 MUST 留到 IF8A-11 按乘客所选渠道执行
            verify(payCenterFlow, never()).preOrder(any(), anyString());
        }

        @Test
        void doesNotInheritOriginalVendor() {
            SupplementOrderReqDTO req = validReq();
            setupOrigOrders();
            when(localWriter.persist(any(), anyList(), any())).thenReturn("SP202609150001");

            service.requestPayOrder(req);

            ArgumentCaptor<SupplementOrder> captor = ArgumentCaptor.forClass(SupplementOrder.class);
            verify(localWriter).persist(captor.capture(), anyList(), any());
            assertEquals(VENDOR, captor.getValue().getPaymentVendor(),
                    "PAYMENT_VENDOR 只作原支付方式留痕，不参与发报");
        }

        /**
         * 建单不做业务防重（2026-11-20 裁决）：同一批欠费重复提交 MUST 各生成一张补款单
         * （APP 侧本来就没有防重逻辑，补款是抢占式，谁先付款谁结清），NEVER 返回 8003。
         */
        @Test
        void repeatedSubmitCreatesAnotherOrderInsteadOfRejecting() {
            SupplementOrderReqDTO req = validReq();
            setupOrigOrders();
            when(localWriter.persist(any(), anyList(), any()))
                    .thenReturn("SP202609150001")
                    .thenReturn("SP202609150002");

            SupplementOrderRespDTO first = service.requestPayOrder(req);
            SupplementOrderRespDTO second = service.requestPayOrder(req);

            assertEquals("0000", first.getRetCode());
            assertEquals("0000", second.getRetCode());
            assertEquals("SP202609150001", first.getOrderNo());
            assertEquals("SP202609150002", second.getOrderNo(),
                    "第二次提交 MUST 拿到自己的新单号，不能复用或回传别人的单号");
            verify(localWriter, org.mockito.Mockito.times(2)).persist(any(), anyList(), any());
            verify(payCenterFlow, never()).preOrder(any(), anyString());
        }
    }


    @Nested
    class RequestPayInfo {

        private static final String SUP_ORDER = "SP202609150001";

        @Test
        void initPreordersWithPassengerChannel() {
            when(supplementOrderMapper.selectByOrderNo(SUP_ORDER))
                    .thenReturn(order("INIT", null, null))
                    .thenReturn(order("PROCESSING", "04", "PAY_INFO_WX"));
            when(payCenterFlow.preOrder(any(), anyString()))
                    .thenReturn(new com.chinasofti.huateng.rpc.outcome.RpcOutcome.Ok());

            JSONObject resp = service.requestPayInfo(payInfoReq(SUP_ORDER, "04"));

            assertEquals("0000", resp.getString("retCode"));
            assertEquals("04", resp.getString("payChannelCode"));
            assertEquals("PAY_INFO_WX", resp.getString("paymentInfo"));
            verify(payCenterFlow).preOrder(any(), eq("04"));
        }

        @Test
        void initPrepayRejectedReturnsFail() {
            when(supplementOrderMapper.selectByOrderNo(SUP_ORDER)).thenReturn(order("INIT", null, null));
            when(payCenterFlow.preOrder(any(), anyString())).thenReturn(new com.chinasofti.huateng.rpc.outcome.RpcOutcome.BizRejected("9999", "渠道不支持"));

            JSONObject resp = service.requestPayInfo(payInfoReq(SUP_ORDER, "04"));

            assertEquals("9999", resp.getString("retCode"));
        }

        @Test
        void processingSameChannelReplaysPaymentInfo() {
            when(supplementOrderMapper.selectByOrderNo(SUP_ORDER))
                    .thenReturn(order("PROCESSING", "04", "PAY_INFO_WX"));

            JSONObject resp = service.requestPayInfo(payInfoReq(SUP_ORDER, "04"));

            assertEquals("0000", resp.getString("retCode"));
            assertEquals("PAY_INFO_WX", resp.getString("paymentInfo"));
            verify(payCenterFlow, never()).preOrder(any(), anyString());
        }

        @Test
        void processingDifferentChannelRepreordersWithNewChannel() {
            when(supplementOrderMapper.selectByOrderNo(SUP_ORDER))
                    .thenReturn(order("PROCESSING", "07", "PAY_INFO_UNION"))
                    .thenReturn(order("PROCESSING", "04", "PAY_INFO_WX"));
            when(payCenterFlow.preOrder(any(), anyString()))
                    .thenReturn(new com.chinasofti.huateng.rpc.outcome.RpcOutcome.Ok());

            JSONObject resp = service.requestPayInfo(payInfoReq(SUP_ORDER, "04"));

            assertEquals("0000", resp.getString("retCode"));
            assertEquals("04", resp.getString("payChannelCode"));
            assertEquals("PAY_INFO_WX", resp.getString("paymentInfo"));
            verify(payCenterFlow).preOrder(any(), eq("04"));
        }

        @Test
        void channelSwitchRefusedByPayCenterReturnsHumanMessage() {
            when(supplementOrderMapper.selectByOrderNo(SUP_ORDER))
                    .thenReturn(order("PROCESSING", "07", "PAY_INFO_UNION"));
            when(payCenterFlow.preOrder(any(), anyString()))
                    .thenReturn(new com.chinasofti.huateng.rpc.outcome.RpcOutcome.BizRejected(
                            SupplementPayCenterFlow.CODE_CHANNEL_LOCKED, "支付中心仍挂旧渠道"));

            JSONObject resp = service.requestPayInfo(payInfoReq(SUP_ORDER, "04"));

            assertEquals("9999", resp.getString("retCode"));
            assertEquals("支付方式未能切换，请稍后重试或联系工作人员", resp.getString("retMsg"));
        }

        @Test
        void blankChannelRejected() {
            when(supplementOrderMapper.selectByOrderNo(SUP_ORDER)).thenReturn(order("INIT", null, null));

            JSONObject resp = service.requestPayInfo(payInfoReq(SUP_ORDER, " "));

            assertEquals("9999", resp.getString("retCode"));
            verify(payCenterFlow, never()).preOrder(any(), anyString());
        }

        private RequestPayInfoReqDTO payInfoReq(String orderNo, String channel) {
            RequestPayInfoReqDTO req = new RequestPayInfoReqDTO();
            req.setOrderNo(orderNo);
            req.setPayChannelCode(channel);
            return req;
        }

        private SupplementOrder order(String payStatus, String vendor, String paymentInfo) {
            SupplementOrder o = new SupplementOrder();
            o.setOrderNo(SUP_ORDER);
            o.setPayStatus(payStatus);
            o.setPaymentVendor(vendor);
            o.setPayChannelCode(vendor);
            o.setPaymentInfo(paymentInfo);
            o.setTotalAmount(800L);
            return o;
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
                gateTxnPay(ORIG_1, "RETRY"),
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
