package com.chinasofti.huateng.paysign.port;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chinasofti.huateng.model.pay.GateTxnPayFailedOrderReqDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayFailedOrderRespDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayRespDTO;
import com.chinasofti.huateng.model.pay.GateTxnPaySyncStatusReqDTO;
import com.chinasofti.huateng.rpc.outcome.RpcOutcome;
import com.chinasofti.huateng.rpc.pay.GateTxnPayClient;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;


class GateTxnPayPortsGuardTest {

    private static final String ORDER = "GT20260917000000001";
    private static final String USER = "U-TEST-0001";
    private static final String ALIPAY_VENDOR = "03";

    private final GateTxnPayClient client = mock(GateTxnPayClient.class);
    private final DebitSyncPort debitSyncPort = new DebitSyncRpcAdapter(client);
    private final UnsettledOrderPort unsettledOrderPort = new UnsettledOrderRpcAdapter(client);


    @Test
    void syncSuccessIsOkAndPassesAllThreeFields() {
        GateTxnPayRespDTO response = new GateTxnPayRespDTO();
        response.setRetCode("0000");
        when(client.syncDebitStatus(any(GateTxnPaySyncStatusReqDTO.class))).thenReturn(response);

        RpcOutcome outcome = debitSyncPort.syncDebitStatus(ORDER, "SUCCESS", "支付结果回调");

        assertTrue(outcome.isOk());
        ArgumentCaptor<GateTxnPaySyncStatusReqDTO> captor =
                ArgumentCaptor.forClass(GateTxnPaySyncStatusReqDTO.class);
        verify(client).syncDebitStatus(captor.capture());
        assertEquals(ORDER, captor.getValue().getOrderNo());
        assertEquals("SUCCESS", captor.getValue().getPayStatus());
        assertEquals("支付结果回调", captor.getValue().getRemark());
    }

    @Test
    void syncNonSuccessCodeIsBizRejectedWithRemoteCode() {
        GateTxnPayRespDTO response = new GateTxnPayRespDTO();
        response.setRetCode("9999");
        response.setRetMsg("订单不存在");
        when(client.syncDebitStatus(any(GateTxnPaySyncStatusReqDTO.class))).thenReturn(response);

        RpcOutcome outcome = debitSyncPort.syncDebitStatus(ORDER, "SUCCESS", "支付结果回调");

        RpcOutcome.BizRejected rejected = assertInstanceOf(RpcOutcome.BizRejected.class, outcome);
        assertEquals("9999", rejected.retCode());
        assertEquals("订单不存在", rejected.retMsg());
    }

    @Test
    void syncNullResponseIsRejectedNeverOk() {
        when(client.syncDebitStatus(any(GateTxnPaySyncStatusReqDTO.class))).thenReturn(null);

        RpcOutcome outcome = debitSyncPort.syncDebitStatus(ORDER, "SUCCESS", "支付结果回调");

        assertInstanceOf(RpcOutcome.BizRejected.class, outcome);
    }

    @Test
    void syncExceptionIsUnreachableAndCarriesCause() {
        when(client.syncDebitStatus(any(GateTxnPaySyncStatusReqDTO.class)))
                .thenThrow(new RuntimeException("connection reset"));

        RpcOutcome outcome = debitSyncPort.syncDebitStatus(ORDER, "SUCCESS", "支付结果回调");

        RpcOutcome.Unreachable unreachable = assertInstanceOf(RpcOutcome.Unreachable.class, outcome);
        assertNotNull(unreachable.cause());
    }


    @Test
    void unsettledAnsweredTrue() {
        when(client.hasFailedOrder(any(GateTxnPayFailedOrderReqDTO.class)))
                .thenReturn(failedOrder("0000", true));

        UnsettledOrderAnswer answer = unsettledOrderPort.hasUnsettledOrder(USER, ALIPAY_VENDOR, null);

        assertEquals(new UnsettledOrderAnswer.Answered(true), answer);
    }

    @Test
    void unsettledAnsweredFalse() {
        when(client.hasFailedOrder(any(GateTxnPayFailedOrderReqDTO.class)))
                .thenReturn(failedOrder("0000", false));

        UnsettledOrderAnswer answer = unsettledOrderPort.hasUnsettledOrder(USER, ALIPAY_VENDOR, null);

        assertEquals(new UnsettledOrderAnswer.Answered(false), answer);
    }


    @Test
    void unsettledNonSuccessCodeIsRejectedNeverAnsweredFalse() {
        when(client.hasFailedOrder(any(GateTxnPayFailedOrderReqDTO.class)))
                .thenReturn(failedOrder("9999", false));

        UnsettledOrderAnswer answer = unsettledOrderPort.hasUnsettledOrder(USER, ALIPAY_VENDOR, null);

        UnsettledOrderAnswer.Rejected rejected =
                assertInstanceOf(UnsettledOrderAnswer.Rejected.class, answer);
        assertEquals("9999", rejected.retCode());
    }

    @Test
    void unsettledNullResponseIsRejected() {
        when(client.hasFailedOrder(any(GateTxnPayFailedOrderReqDTO.class))).thenReturn(null);

        assertInstanceOf(UnsettledOrderAnswer.Rejected.class,
                unsettledOrderPort.hasUnsettledOrder(USER, ALIPAY_VENDOR, null));
    }

    @Test
    void unsettledExceptionIsUnknown() {
        when(client.hasFailedOrder(any(GateTxnPayFailedOrderReqDTO.class)))
                .thenThrow(new RuntimeException("connect timed out"));

        UnsettledOrderAnswer answer = unsettledOrderPort.hasUnsettledOrder(USER, ALIPAY_VENDOR, null);

        assertNotNull(assertInstanceOf(UnsettledOrderAnswer.Unknown.class, answer).cause());
    }


    @Test
    void unsettledRequestTimeIsPassedThrough() {
        LocalDateTime requestTime = LocalDateTime.of(2026, 9, 17, 10, 30, 0);
        when(client.hasFailedOrder(any(GateTxnPayFailedOrderReqDTO.class)))
                .thenReturn(failedOrder("0000", false));

        unsettledOrderPort.hasUnsettledOrder(USER, ALIPAY_VENDOR, requestTime);

        ArgumentCaptor<GateTxnPayFailedOrderReqDTO> captor =
                ArgumentCaptor.forClass(GateTxnPayFailedOrderReqDTO.class);
        verify(client).hasFailedOrder(captor.capture());
        assertEquals(USER, captor.getValue().getThirdUserId());
        assertEquals(ALIPAY_VENDOR, captor.getValue().getPaymentVendor());
        assertEquals(requestTime, captor.getValue().getRequestTime());
    }

    private GateTxnPayFailedOrderRespDTO failedOrder(String resultCode, boolean hasFailedOrder) {
        GateTxnPayFailedOrderRespDTO response = new GateTxnPayFailedOrderRespDTO();
        response.setResultCode(resultCode);
        response.setHasFailedOrder(hasFailedOrder);
        return response;
    }
}
